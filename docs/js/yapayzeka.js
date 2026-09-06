/*
 * OpenAI ile konuşan ince katman.
 *
 * Anahtar yalnızca istek başlığında kullanılıyor; telefonun kendi
 * deposunda duruyor, hiçbir yere gönderilmiyor ve hata mesajlarına
 * konmuyor.
 *
 * Yönergeler Android sürümündekilerin aynısı — iki uygulamanın aynı şeyi
 * söylemesi gerekiyor.
 */

const UC = "https://api.openai.com/v1/chat/completions";

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
    "in the middle of an English book.",
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
    "the English text itself.",
  ].join(" ");

  return sor(ayarlar, yonerge, istekMetni(secim, baglam, eser), 700);
}

/** "Bu nedir" sorusunun cevabı; elli-yüz kelime. */
export function bilgi(ayarlar, secim, baglam, eser) {
  const yonerge = [
    "Explain the given English word, phrase or sentence to a Turkish reader",
    "who met it in a book. Answer IN TURKISH, in 50 to 100 words, as one or",
    "two plain paragraphs.",
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
  // Örnek cümleler yine İngilizce, çünkü öğrenilen şey onlar; ama her
  // birinin altında Türkçesi var. Eş anlamlı, karşıt ve kökendaş
  // kelimeler de İngilizce kalıyor — onlar İngilizce kelime — ama
  // yanlarında karşılıkları yazıyor.
  const yonerge = [
    "You are a bilingual English-Turkish dictionary for an adult Turkish",
    "learner of English. Return JSON with exactly these keys and nothing",
    "else. EVERY explanation is written IN TURKISH:",
    '"karsilik": the Turkish equivalent(s), a few words.',
    '"tanim": what the word means, IN TURKISH, one or two plain sentences.',
    '"ornekler": 3 objects, each {"en": an English example sentence,',
    '"tr": its Turkish translation}.',
    '"kok": the origin IN TURKISH, one line, e.g.',
    '"morph- (Yun. morphē = şekil)". Empty string if there is nothing to say.',
    '"aile": other English words from the same root, each written as',
    '"word — Türkçe karşılığı".',
    '"esanlam": English synonyms, "karsit": English antonyms,',
    '"birliktelik": typical English collocations —',
    'each of these also written as "english — Türkçe karşılığı".',
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
