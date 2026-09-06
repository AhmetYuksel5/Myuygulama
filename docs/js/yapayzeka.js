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

/** Metinde Arap harfi var mı? Kart yönergesini bu seçiyor. */
const ARAPCA = /[؀-ۿ]/;

const dizi = d =>
  (Array.isArray(d) ? d.filter(x => typeof x === "string" && x.trim()).map(x => x.trim()) : []);

/**
 * Arapça kelime maddesi. Android'deki yönergenin aynısı.
 *
 * İngilizcedekinin çevirisi değil: Arapçada öğrencinin ezberlediği şey
 * başka. Harekesiz yazım okunuşu vermiyor, isimde çoğul ve fiilde mastar
 * kuralsız, kök üç sessizden oluşuyor ve aynı kökten türeyenler gerçek
 * bir aile kuruyor — İngilizcedeki köken bölümünün Arapçada karşılığı
 * çok daha güçlü. Şekil benzerliği de burada kritik: noktası değişen iki
 * harf ayrı kelime yapıyor.
 *
 * Android'den tek farkı örneklerde: orada yalnız Arapça cümle var,
 * burada altına Türkçesi de isteniyor. Okuyan öyle istedi.
 */
function arapcaYonergesi() {
  return [
    "You are a bilingual Arabic-Turkish lexicographer writing a study",
    "card for a Turkish learner of Arabic. The input may be a single",
    "word or a phrase; treat it as one unit. Modern Standard Arabic is",
    "the default, but if the input is dialect, say so and give the MSA",
    "equivalent.",
    "Return STRICT JSON with keys:",

    "t (Turkish meanings, 1-3, comma separated. Write the Turkish a",
    "Turkish speaker would actually say, not a word-by-word gloss),",

    'y (the memorisation line, in exactly this order separated by " — ":',
    "the input FULLY VOWELLED with harakat; its Latin transcription;",
    "then for a noun its plural, for a verb its maṣdar and present",
    'tense. Example for كتاب: "كِتَاب — kitāb — ج. كُتُب". Example for',
    'كتب: "كَتَبَ — kataba — يَكْتُب، الكِتَابَة". This line is the single',
    "most useful thing on the card: unvowelled Arabic does not show",
    "its own pronunciation and Arabic plurals are irregular),",

    "d (a short definition in SIMPLE Arabic, max 12 words, fully",
    "understandable to an intermediate learner),",

    'e (array of exactly 3 objects {"asil": a natural Arabic example',
    "sentence using it, 6-14 words, with harakat on the input word only,",
    '"tr": that sentence in natural Turkish}),',

    "s (SYNONYMS: 2-4 Arabic words that could replace the input with",
    "roughly the same meaning, Arabic only, no translation),",

    "a (ANTONYMS: 1-3 Arabic opposites, Arabic only; empty only if there",
    "is none),",

    "r (RELATED: 3-5 Arabic words from the same topic that are NOT",
    "interchangeable with the input, Arabic only),",

    "k (ROOT: the triliteral (or quadriliteral) root, written with",
    "spaces between the radicals and then its core sense in Turkish, in",
    'exactly this shape: "ك ت ب (yazmak)". Empty string only for',
    "borrowed words with no Arabic root, and then say so),",

    "f (WORD FAMILY: 4-8 OTHER words built on that SAME root, the place",
    "where Arabic rewards a learner most. Give the derived forms a",
    "learner actually meets — the maṣdar, the active and passive",
    "participle, the noun of place, the instrument noun — not a",
    'mechanical list. Write each as "kelime — Türkçe":',
    '"مَكْتَب — yazıhane, ofis". Never repeat the input),',

    "x (LOOK-ALIKES: 2-3 Arabic words that LOOK like the input on the",
    "page even though their meaning and root are unrelated. In Arabic",
    "this matters more than in Latin script: words differ by a single",
    "dot or by letters that share a shape (ب ت ث، ج ح خ، د ذ، ر ز،",
    "س ش، ص ض، ط ظ، ع غ). Look for exactly that kind of pair —",
    'بَحَث/بَحَت، ضَلَّ/ظَلَّ. Write each as "kelime — Türkçe" and nothing',
    "else; do not spell out which letter differs, the reader sees it.",
    "Never list the input itself or a form of it),",

    "c (COLLOCATIONS grouped by grammatical pattern: array of objects",
    "with g and w. Decide the input's part of speech first, then use",
    'only these groups: for a NOUN "fiil +" (verbs that take it as',
    'object), "sıfat +" (adjectives that describe it), "tamlama" (the',
    "nouns it is commonly annexed to, iḍāfa); for a VERB \"+ isim\" (its",
    'typical objects), "+ harf-i cer" (the preposition it governs — in',
    "Arabic this changes the meaning and must be learned with the verb),",
    '"zarf +"; for an ADJECTIVE "+ isim", "zarf +".',
    "w is an array of 3-6 collocates, Arabic only, no translation.",
    "Give 2-4 groups).",

    "Never pad a section to reach a count: fewer good items beat filler.",
    "This does not apply to x — give the 2-3 closest look-alikes you",
    "found. No markdown, no extra keys, no commentary.",
  ].join(" ");
}

/**
 * Arapça olmayan kelimeler için kart.
 *
 * Kartın tamamı Türkçe anlatıyor. Önce tanım ve örnekler kendi dilinde
 * bırakılmıştı; kelimeyi bilmeyen biri için o tanım da bilinmeyen bir
 * cümle oluyordu.
 */
function genelYonerge() {
  return [
    "You are a bilingual dictionary for an adult Turkish reader who is",
    "learning the language of the Input.",
    DIL_KURALI,
    "Return JSON with exactly these keys and nothing else. EVERY",
    "explanation is written IN TURKISH:",
    '"karsilik": the Turkish equivalent(s), a few words.',
    '"tanim": what the word means, IN TURKISH, one or two plain sentences.',
    '"ornekler": 3 objects, each {"asil": an example sentence in the',
    'language of the Input, "tr": its Turkish translation}.',
    '"kok": the origin IN TURKISH, one line, e.g.',
    '"morph- (Yun. morphē = şekil)". Empty string if there is nothing to say.',
    '"aile": other words from the same root, in the language of the Input,',
    'each written as "kelime — Türkçe karşılığı".',
    '"esanlam": synonyms, "karsit": antonyms, "birliktelik": typical',
    "collocations — all three in the language of the Input, and each also",
    'written as "kelime — Türkçe karşılığı".',
    "Choose the sense that fits the passage. Keep every list at most six",
    "items. Plain text inside the values; no markdown.",
  ].join(" ");
}

/**
 * Kelime kartı.
 *
 * İki yönerge var, kelimenin harfine bakılıyor: Arap harfi görülünce
 * Android'deki Arapça maddesi, yoksa genel madde. Dönen JSON iki
 * durumda da aynı biçime çevriliyor; kartı çizen taraf hangi yoldan
 * geldiğini bilmek zorunda kalmasın.
 */
export async function kart(ayarlar, secim, baglam, eser) {
  const arapca = ARAPCA.test(secim);
  // Arapça kartta alan sayısı iki katı; dar sınırda son bölümler
  // yarıda kesiliyor ve JSON hiç ayrıştırılamıyor.
  const sonuc = await iste(
    ayarlar,
    arapca ? arapcaYonergesi() : genelYonerge(),
    istekMetni(secim, baglam, eser),
    arapca ? 1600 : 900,
  );
  if (sonuc.hata) return sonuc;
  const veri = jsonCoz(sonuc.metin);
  if (!veri) return { hata: "Kart okunamadı." };
  return { kart: arapca ? arapcaKarti(veri) : genelKart(veri) };
}

/** Arapça yönergenin kısa anahtarlarını kartın alanlarına dağıtır. */
function arapcaKarti(v) {
  return {
    arapca: true,
    karsilik: (v.t || "").trim(),
    okunus: (v.y || "").trim(),
    tanim: (v.d || "").trim(),
    ornekler: Array.isArray(v.e) ? v.e : [],
    esanlam: dizi(v.s),
    karsit: dizi(v.a),
    ilgili: dizi(v.r),
    kok: (v.k || "").trim(),
    aile: dizi(v.f),
    karistirma: dizi(v.x),
    birliktelik: Array.isArray(v.c)
      ? v.c
        .map(g => ({ grup: (g?.g || "").trim(), kelimeler: dizi(g?.w) }))
        .filter(g => g.grup && g.kelimeler.length)
      : [],
  };
}

/** Genel yönergenin çıktısını aynı biçime getirir. */
function genelKart(v) {
  const birlikte = dizi(v.birliktelik);
  return {
    arapca: false,
    karsilik: (v.karsilik || "").trim(),
    okunus: "",
    tanim: (v.tanim || "").trim(),
    ornekler: Array.isArray(v.ornekler) ? v.ornekler : [],
    esanlam: dizi(v.esanlam),
    karsit: dizi(v.karsit),
    ilgili: [],
    kok: (v.kok || "").trim(),
    aile: dizi(v.aile),
    karistirma: [],
    birliktelik: birlikte.length ? [{ grup: "Birlikte", kelimeler: birlikte }] : [],
  };
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
