/*
 * OpenAI ile konuşan ince katman.
 *
 * Anahtar yalnızca istek başlığında kullanılıyor; telefonun kendi
 * deposunda duruyor, hiçbir yere gönderilmiyor ve hata mesajlarına
 * konmuyor.
 *
 * Yönergeler Android sürümündekilerin aynısı — iki uygulamanın aynı şeyi
 * söylemesi gerekiyor.
 *
 * Ortak kural: ortada iki dil var, Türkçe ve seçilen metnin kendi dili.
 * Üçüncü bir dil hiçbir yerde geçmiyor. Yönergeler önce "İngilizce
 * kitap okuyan biri" varsayımıyla yazılmıştı; Arapça bir kelime sorulunca
 * model tanımı Türkçe, örnekleri İngilizce veriyordu — soranın istemediği
 * bir dil.
 */

const UC = "https://api.openai.com/v1/chat/completions";

// Her yönergenin sonuna eklenen dil kuralı. Kaynak dili biz söylemiyoruz;
// modelin metne bakıp anlaması yeter, biz yalnız "başka dile sapma"
// diyoruz.
const DIL_KURALI = [
  "The Input may be in any language — English, Arabic, anything.",
  "Only two languages may appear in your answer: Turkish, and the",
  "language of the Input itself. Never bring in a third language; an",
  "Arabic word is never explained with English words.",
].join(" ");

async function sor(ayarlar, yonerge, istek, sinir) {
  if (!ayarlar.anahtar) return { hata: "OpenAI anahtarı girilmemiş." };
  const govde = {
    model: ayarlar.model,
    temperature: 0.3,
    max_tokens: sinir,
    messages: [
      { role: "system", content: yonerge },
      { role: "user", content: istek },
    ],
  };
  try {
    const yanit = await fetch(UC, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${ayarlar.anahtar}`,
      },
      body: JSON.stringify(govde),
    });
    const veri = await yanit.json();
    if (!yanit.ok) {
      return { hata: okunurHata(yanit.status, veri) };
    }
    const metin = (veri.choices?.[0]?.message?.content || "").trim();
    return metin ? { metin } : { hata: "Yanıt boş geldi." };
  } catch (e) {
    return { hata: "Bağlantı kurulamadı." };
  }
}

function okunurHata(kod, veri) {
  if (kod === 401) return "Anahtar kabul edilmedi. Ayarlardan kontrol et.";
  if (kod === 429) return "Kota doldu ya da çok sık istek gitti.";
  if (kod === 404) return "Bu model hesabında yok; ayarlardan başkasını seç.";
  return veri?.error?.message || `İstek başarısız (${kod}).`;
}

/** Seçilen metnin çevirisi. Çevrilen şey her zaman seçimin kendisi. */
export function cevir(ayarlar, secim, baglam, eser) {
  const yonerge = [
    "You are a literary translator working into Turkish for a reader who is",
    "in the middle of a book.",
    DIL_KURALI,
    "Write the Turkish a good translator would write — natural, idiomatic",
    "Turkish that reads as if it had been written in Turkish — never a",
    "word-for-word rendering.",
    "Translate ONLY the text given as Input. The passage is there so you can",
    "choose the right sense of an ambiguous word; never translate the",
    "passage, never extend the translation beyond the Input, and never trim",
    "it either — what comes back must correspond exactly to the Input.",
    "A single word gets its Turkish equivalent(s) for THIS passage, in a few",
    "words. Anything longer gets a COMPLETE translation: every clause,",
    "nothing summarised, nothing left out.",
    "Keep the register of the original — formal stays formal, slang stays",
    "slang, coarse stays coarse; do not soften it.",
    "An idiom becomes the Turkish idiom that means the same thing.",
    "Do not explain, do not comment, do not add anything that is not in the",
    "text. No quotation marks, no markdown, no preamble, and never repeat",
    "the original text itself.",
  ].join(" ");

  return sor(ayarlar, yonerge, istekMetni(secim, baglam, eser), 700);
}

/** "Bu nedir" sorusunun cevabı; elli-yüz kelime. */
export function bilgi(ayarlar, secim, baglam, eser) {
  const yonerge = [
    "Explain the given word, phrase or sentence to a Turkish reader who met",
    "it in a book. Answer IN TURKISH, in 50 to 100 words, as one or two",
    "plain paragraphs.",
    DIL_KURALI,
    "Say what it means and then the thing worth knowing about it: where a",
    "term comes from, what a concept is for, who a person was, what a",
    "reference points to, why a phrase is said that way.",
    "Use the passage only to pick the right reading; do not retell the",
    "passage and do not comment on the book.",
    "Do not merely give the Turkish translation — the reader already has it.",
    "Start with the substance, not with an opening formula.",
    "If the text is ordinary and there is nothing behind it, say so in one",
    "sentence instead of inventing something.",
    "No markdown, no lists, no headings.",
  ].join(" ");

  return sor(ayarlar, yonerge, istekMetni(secim, baglam, eser), 500);
}

/**
 * Kelime kartı: karşılık, tanım, örnekler, kök, eş ve karşıt anlamlılar.
 * JSON isteniyor ki kartın alanlarına dağıtılabilsin.
 */
export async function kart(ayarlar, secim, baglam, eser) {
  // Kartın tamamı Türkçe anlatıyor.
  //
  // Önce tanım ve örnekler İngilizce bırakılmıştı: "öğrenilen kısım
  // İngilizce kalsın" diye. Kart o hâlde okunmuyordu — kelimeyi bilmeyen
  // biri için İngilizce tanım da bilinmeyen bir cümle.
  //
  // Örnek cümleler, eş ve karşıt anlamlılar kelimenin kendi dilinde
  // kalıyor — öğrenilen şey onlar — ama her birinin yanında Türkçesi var.
  // Alan adı "asil": "en" yazıyordu ve model Arapça bir kelimeye bile
  // İngilizce örnek uyduruyordu, çünkü alanın adı öyle diyordu.
  const yonerge = [
    "You are a bilingual dictionary for an adult Turkish reader who is",
    "learning the language of the Input.",
    DIL_KURALI,
    "Return JSON with exactly these keys and nothing else. EVERY",
    "explanation is written IN TURKISH:",
    '"karsilik": the Turkish equivalent(s), a few words.',
    '"tanim": what the word means, IN TURKISH, one or two plain sentences.',
    '"ornekler": 3 objects, each {"asil": an example sentence in the',
    'language of the Input, "tr": its Turkish translation}.',
    '"kok": the origin IN TURKISH, one line — for an Arabic word its',
    'root letters, for a European word its etymology, e.g.',
    '"morph- (Yun. morphē = şekil)". Empty string if there is nothing to say.',
    '"aile": other words from the same root, in the language of the Input,',
    'each written as "kelime — Türkçe karşılığı".',
    '"esanlam": synonyms, "karsit": antonyms, "birliktelik": typical',
    "collocations — all three in the language of the Input, and each also",
    'written as "kelime — Türkçe karşılığı".',
    "Choose the sense that fits the passage. Keep every list at most six",
    "items. Plain text inside the values; no markdown.",
  ].join(" ");

  const sonuc = await sor(ayarlar, yonerge, istekMetni(secim, baglam, eser), 900);
  if (sonuc.hata) return sonuc;
  try {
    // Model bazen JSON'u kod çitiyle sarıyor.
    const temiz = sonuc.metin.replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
    return { kart: JSON.parse(temiz) };
  } catch {
    return { hata: "Kart okunamadı." };
  }
}

function istekMetni(secim, baglam, eser) {
  let metin = `Input: ${secim}`;
  if (baglam) metin += `\nPassage: ${baglam}`;
  if (eser) metin += `\nFrom: ${eser}`;
  return metin;
}
