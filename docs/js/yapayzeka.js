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

async function iste(ayarlar, yonerge, istek, sinir) {
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

  return iste(ayarlar, yonerge, istekMetni(secim, baglam, eser), 700);
}

/**
 * Okuyucunun seçim hakkındaki kendi sorusu.
 *
 * "Bilgi al" düğmesinin yerine geldi: hazır bir "bu nedir" yazısı çoğu
 * zaman sorulmayan bir soruya cevap veriyordu. Android'deki karttaki
 * soru kutusunun aynısı.
 */
export function soru(ayarlar, secim, baglam, eser, soruMetni, kart) {
  const yonerge = [
    "You are a bilingual teacher. The reader is studying a word or sentence",
    "from a book and has a follow-up question about it. Answer IN TURKISH,",
    "directly, at most 120 words, for an adult learner.",
    DIL_KURALI,
    "If the answer involves a figurative sense, give the literal meaning and",
    "how the sense travelled from it, in ordinary Turkish, without jargon.",
    "Never explain that a swear word is rude; the reader knows.",
    "Use the passage and the book only to pick the right reading — do not",
    "talk about the book.",
    "If what the card already says agrees with your answer, do not repeat",
    "it — add what it does not cover. If it CONTRADICTS the card, say so in",
    "the first sentence and explain which reading is right.",
    "Do not simply accept the way the question is framed: if its premise is",
    "wrong, say that first.",
    "No markdown, no lists, no preamble.",
  ].join(" ");

  let metin = istekMetni(secim, baglam, eser);
  if (kart) metin += `\nWhat the card already says: ${JSON.stringify(kart)}`;
  metin += `\nQuestion: ${soruMetni}`;
  return iste(ayarlar, yonerge, metin, 500);
}

/**
 * Cümle ya da öbek: tam çeviri ve altında zor olabilecek ifadeler.
 *
 * Tek kelimede karşılık yetiyor; cümlede çeviri okunduktan sonra "şu
 * ifade neydi" sorusu kalıyordu. Notlar o soruya peşinen cevap: yaygın
 * iki bin kelimenin dışındaki kelimeler, deyimler, Arapçada fiilin
 * babı. Zor bir şey yoksa liste boş — zorlama not istenmiyor.
 */
export async function cumle(ayarlar, secim, baglam, eser) {
  const yonerge = [
    "You are a literary translator working into Turkish for a reader who is",
    "in the middle of a book.",
    DIL_KURALI,
    "Return JSON with exactly two keys.",
    '"ceviri": the COMPLETE Turkish translation of the Input — every clause,',
    "nothing summarised, nothing left out; natural, idiomatic Turkish, the",
    "register of the original kept; an idiom becomes the Turkish idiom.",
    "Translate ONLY the Input; the passage is there for choosing senses.",
    '"zorlar": an array of the expressions IN THE INPUT a Turkish learner',
    "may not know, each {\"ifade\": the expression exactly as it appears,",
    '"anlam": its meaning here IN TURKISH, a few words}.',
    "Include: words outside the 2000 most common words of the language;",
    "idioms and phrasal verbs whose meaning is not the sum of their parts;",
    "for an Arabic verb, add its verb form (bāb) in parentheses after the",
    'meaning, e.g. "(bâb-ı tef\'îl)". Do NOT include ordinary words the',
    "reader surely knows. If nothing in the Input is hard, return an empty",
    "array — that is the normal case, not a failure. At most six items.",
    "Plain text inside the values; no markdown.",
  ].join(" ");

  const sonuc = await iste(ayarlar, yonerge, istekMetni(secim, baglam, eser), 900);
  if (sonuc.hata) return sonuc;
  const veri = jsonCoz(sonuc.metin);
  if (!veri || typeof veri.ceviri !== "string") return { hata: "Çeviri okunamadı." };
  return { ceviri: veri.ceviri.trim(), zorlar: Array.isArray(veri.zorlar) ? veri.zorlar : [] };
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

  const sonuc = await iste(ayarlar, yonerge, istekMetni(secim, baglam, eser), 900);
  if (sonuc.hata) return sonuc;
  const veri = jsonCoz(sonuc.metin);
  return veri ? { kart: veri } : { hata: "Kart okunamadı." };
}

/** Model bazen JSON'u kod çitiyle sarıyor; çiti soyup ayrıştırır. */
function jsonCoz(metin) {
  try {
    return JSON.parse(metin.replace(/^```(?:json)?/i, "").replace(/```$/, "").trim());
  } catch {
    return null;
  }
}

function istekMetni(secim, baglam, eser) {
  let metin = `Input: ${secim}`;
  if (baglam) metin += `\nPassage: ${baglam}`;
  if (eser) metin += `\nFrom: ${eser}`;
  return metin;
}
