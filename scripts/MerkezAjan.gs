/**
 * Merkez Ekran Ajanı — Google Drive ve Gmail Köprüsü (Google Apps Script)
 *
 * Aynı betik hem iş hesabında (yesiltemizlik@gmail.com — Drive + İş Gmail)
 * hem de kişisel hesapta (ahmetyuksel5@gmail.com — Kişisel Gmail) ayrı
 * Web Uygulaması olarak dağıtılabilir.
 *
 * Güvenlik: yalnızca okuma ve taslak oluşturma vardır; otomatik e-posta
 * gönderme veya silme eylemi bilerek eklenmemiştir. Tüm istekler
 * MERKEZ_TOKEN komut dosyası özelliği ile korunur.
 *
 * Kurulum (bir kez, 1 dakika):
 * 1. https://script.google.com adresinde "Yeni proje" aç, adını "MerkezAjan" koy.
 * 2. Bu dosyanın içeriğini Code.gs içine yapıştır ve kaydet.
 * 3. Proje Ayarları (dişli simgesi) → Komut Dosyası Özellikleri → Özellik ekle:
 *      Özellik: MERKEZ_TOKEN
 *      Değer:   kendin belirlediğin uzun rastgele bir parola (depoya asla yazma!)
 * 4. Sağ üstte Dağıt → Yeni dağıtım → Tür: Web uygulaması:
 *      - Yürütme kimliği: Ben
 *      - Erişimi olanlar: Herkes (istekler MERKEZ_TOKEN ile korunur)
 *    Yetki onayını ver ve çıkan Web uygulaması URL'sini (…/exec) kopyala.
 * 5. Telefonda Merkez → Yapay zekâ → Google Drive / Kişisel Gmail köprüsü
 *    alanına URL ve jetonu gir.
 */

var FOLDER_NAME = 'MerkezAjan';
var LOG_FILE_NAME = 'Merkez_Ajan_Log.md';
var MEMORY_FILE_NAME = 'Merkez_Ajan_Hafiza.md';
var MAX_LOG_CHARS = 80000;
var MAX_GMAIL_READ_CHARS = 6000;

var DEFAULT_MEMORY_TEMPLATE = [
  '# Merkez Ekran Ajanı — Hafıza ve Merdiven Metotları',
  '',
  'Bu dosya telefonundaki Ekran Ajanı tarafından her görev başında okunur.',
  'Sık yaptığın işlerin kısa yollarını ve takıldığın engelleri aşma kurallarını buraya madde madde yazabilirsin.',
  '',
  '## Genel Kurallar',
  '- Google Drive dosyası aranıyorsa ekranda Drive uygulamasını açmak yerine önce `drive_search` ve `drive_read` araçlarını kullan; gerekirse dönen linki `open_url` ile tek adımda aç.',
  '- E-posta aranıyorsa ekranda Gmail uygulamasını açmak yerine önce `gmail_search` ve `gmail_read` araçlarını kullan; hangi hesap olduğu belli değilse ikisinde de ara veya `ask` ile sor.',
  '- E-posta yazılması istendiğinde asla doğrudan gönderme; `gmail_draft` ile taslak oluşturup kullanıcıya bildir.',
  '- Birden fazla eşleşen kişi, hesap veya cihaz çıktığında tahmin etme; `ask` eylemiyle seçenekleri sun.',
  '',
  '## Öğrenilen Metotlar',
].join('\n') + '\n';

function doGet() {
  return jsonOut_({ ok: true, service: 'MerkezAjan' });
}

function doPost(e) {
  try {
    var raw = e && e.postData && e.postData.contents ? e.postData.contents : '{}';
    var body = JSON.parse(raw);

    var expected = PropertiesService.getScriptProperties().getProperty('MERKEZ_TOKEN');
    if (!expected) {
      return jsonOut_({ ok: false, error: 'MERKEZ_TOKEN komut dosyası özelliği tanımlı değil.' });
    }
    if (!body.token || body.token !== expected) {
      return jsonOut_({ ok: false, error: 'Geçersiz köprü jetonu.' });
    }

    var action = String(body.action || '').trim();
    if (action === 'ping') return jsonOut_(handlePing_());
    if (action === 'drive_search') return jsonOut_(handleDriveSearch_(body.query, body.limit));
    if (action === 'drive_read') return jsonOut_(handleDriveRead_(body.fileId, body.maxChars));
    if (action === 'append_log') return jsonOut_(handleAppendLog_(body.entry));
    if (action === 'read_memory') return jsonOut_(handleReadMemory_());
    if (action === 'append_memory') return jsonOut_(handleAppendMemory_(body.note));
    if (action === 'write_memory') return jsonOut_(handleWriteMemory_(body.content));
    if (action === 'gmail_ping') return jsonOut_(handleGmailPing_());
    if (action === 'gmail_search') return jsonOut_(handleGmailSearch_(body.query, body.limit));
    if (action === 'gmail_read') return jsonOut_(handleGmailRead_(body.threadId));
    if (action === 'gmail_draft') return jsonOut_(handleGmailDraft_(body.to, body.subject, body.body));

    return jsonOut_({ ok: false, error: 'Bilinmeyen eylem: ' + action });
  } catch (err) {
    return jsonOut_({ ok: false, error: String(err && err.message ? err.message : err) });
  }
}

function handlePing_() {
  var folder = getOrCreateFolder_();
  var mem = getOrCreateTextFile_(folder, MEMORY_FILE_NAME, DEFAULT_MEMORY_TEMPLATE);
  var log = getOrCreateTextFile_(folder, LOG_FILE_NAME, '# Merkez Ekran Ajanı Günlüğü\n\n');
  var gmailOk = false;
  try {
    GmailApp.getInboxUnreadCount();
    gmailOk = true;
  } catch (ignore) {
    gmailOk = false;
  }
  return {
    ok: true,
    folderId: folder.getId(),
    memoryFileId: mem.getId(),
    logFileId: log.getId(),
    gmail: gmailOk,
  };
}

function handleDriveSearch_(query, limit) {
  var q = String(query || '').trim();
  if (!q) return { ok: false, error: 'Arama sorgusu boş.' };
  var maxItems = Math.min(Math.max(Number(limit) || 10, 1), 20);
  var escaped = q.replace(/\\/g, '\\\\').replace(/'/g, "\\'");

  var results = [];
  var seen = {};

  function collect(iterator) {
    while (iterator.hasNext() && results.length < maxItems) {
      var f = iterator.next();
      var id = f.getId();
      if (seen[id]) continue;
      seen[id] = true;
      results.push({
        id: id,
        name: f.getName(),
        mimeType: f.getMimeType ? f.getMimeType() : 'application/vnd.google-apps.folder',
        url: f.getUrl(),
        modified: f.getLastUpdated ? f.getLastUpdated().toISOString() : '',
      });
    }
  }

  // Önce dosya adına göre ara; yetmezse klasör adına ve dosya içeriğine genişlet.
  collect(DriveApp.searchFiles("trashed = false and title contains '" + escaped + "'"));
  if (results.length < maxItems) {
    collect(DriveApp.searchFolders("trashed = false and title contains '" + escaped + "'"));
  }
  if (results.length < maxItems) {
    collect(DriveApp.searchFiles("trashed = false and fullText contains '" + escaped + "'"));
  }

  results.sort(function (a, b) {
    return a.modified < b.modified ? 1 : (a.modified > b.modified ? -1 : 0);
  });
  return { ok: true, items: results.slice(0, maxItems) };
}

function handleDriveRead_(fileId, maxChars) {
  var id = String(fileId || '').trim();
  if (!id) return { ok: false, error: 'fileId boş.' };
  var cap = Math.min(Math.max(Number(maxChars) || 5000, 200), 12000);
  var file = DriveApp.getFileById(id);
  var mime = file.getMimeType();
  var text = '';

  if (mime === MimeType.GOOGLE_DOCS) {
    text = DocumentApp.openById(id).getBody().getText();
  } else if (mime === MimeType.GOOGLE_SHEETS) {
    var sheet = SpreadsheetApp.openById(id).getSheets()[0];
    var rows = Math.min(sheet.getLastRow(), 60);
    var cols = Math.min(sheet.getLastColumn(), 15);
    if (rows > 0 && cols > 0) {
      var values = sheet.getRange(1, 1, rows, cols).getDisplayValues();
      text = values.map(function (r) { return r.join('\t'); }).join('\n');
    }
  } else if (
    mime.indexOf('text/') === 0 ||
    mime === 'application/json' ||
    mime === 'application/xml' ||
    mime === 'text/markdown' ||
    file.getName().toLowerCase().slice(-3) === '.md'
  ) {
    text = file.getBlob().getDataAsString('UTF-8');
  } else {
    return {
      ok: true,
      name: file.getName(),
      mimeType: mime,
      url: file.getUrl(),
      content: '(İkili dosya — metin olarak okunamadı; open_url ile açılabilir: ' + file.getUrl() + ')',
    };
  }

  var truncated = text.length > cap;
  return {
    ok: true,
    name: file.getName(),
    mimeType: mime,
    url: file.getUrl(),
    truncated: truncated,
    content: truncated ? text.slice(0, cap) + '\n…(kısaltıldı)' : text,
  };
}

function handleAppendLog_(entry) {
  var text = String(entry || '').trim();
  if (!text) return { ok: false, error: 'Log içeriği boş.' };
  var folder = getOrCreateFolder_();
  var file = getOrCreateTextFile_(folder, LOG_FILE_NAME, '# Merkez Ekran Ajanı Günlüğü\n\n');
  var existing = file.getBlob().getDataAsString('UTF-8');
  var header = '# Merkez Ekran Ajanı Günlüğü\n\n';
  var body = existing.indexOf(header) === 0 ? existing.slice(header.length) : existing;
  // En yeni kayıt en üstte durur ki son görev ilk satırda hemen okunsun.
  var combined = header + text + '\n\n---\n\n' + body;
  if (combined.length > MAX_LOG_CHARS) {
    combined = combined.slice(0, MAX_LOG_CHARS);
  }
  file.setContent(combined);
  return { ok: true, fileId: file.getId(), url: file.getUrl() };
}

function handleReadMemory_() {
  var folder = getOrCreateFolder_();
  var file = getOrCreateTextFile_(folder, MEMORY_FILE_NAME, DEFAULT_MEMORY_TEMPLATE);
  return {
    ok: true,
    fileId: file.getId(),
    url: file.getUrl(),
    content: file.getBlob().getDataAsString('UTF-8'),
  };
}

function handleAppendMemory_(note) {
  var clean = String(note || '').trim();
  if (!clean) return { ok: false, error: 'Not boş.' };
  var line = clean.indexOf('- ') === 0 ? clean : '- ' + clean;
  var folder = getOrCreateFolder_();
  var file = getOrCreateTextFile_(folder, MEMORY_FILE_NAME, DEFAULT_MEMORY_TEMPLATE);
  var existing = file.getBlob().getDataAsString('UTF-8');
  if (existing.indexOf(line) === -1) {
    var next = existing.replace(/\s*$/, '') + '\n' + line + '\n';
    file.setContent(next);
    existing = next;
  }
  return { ok: true, fileId: file.getId(), content: existing };
}

function handleWriteMemory_(content) {
  var text = String(content || '');
  var folder = getOrCreateFolder_();
  var file = getOrCreateTextFile_(folder, MEMORY_FILE_NAME, DEFAULT_MEMORY_TEMPLATE);
  file.setContent(text.trim() ? text : DEFAULT_MEMORY_TEMPLATE);
  return { ok: true, fileId: file.getId(), content: file.getBlob().getDataAsString('UTF-8') };
}

function handleGmailPing_() {
  var unread = GmailApp.getInboxUnreadCount();
  return { ok: true, gmail: true, unread: unread };
}

function handleGmailSearch_(query, limit) {
  var q = String(query || '').trim();
  if (!q) return { ok: false, error: 'Gmail arama sorgusu boş.' };
  var maxItems = Math.min(Math.max(Number(limit) || 10, 1), 20);
  var threads = GmailApp.search(q, 0, maxItems);
  var items = [];
  for (var i = 0; i < threads.length; i++) {
    var th = threads[i];
    var msgs = th.getMessages();
    var last = msgs.length > 0 ? msgs[msgs.length - 1] : null;
    var snippet = last
      ? String(last.getPlainBody() || '').replace(/\s+/g, ' ').trim().slice(0, 180)
      : '';
    items.push({
      threadId: th.getId(),
      subject: th.getFirstMessageSubject() || '',
      from: last ? last.getFrom() : '',
      date: formatDateTr_(th.getLastMessageDate()),
      snippet: snippet,
      unread: th.isUnread(),
      messageCount: msgs.length,
    });
  }
  return { ok: true, items: items };
}

function handleGmailRead_(threadId) {
  var id = String(threadId || '').trim();
  if (!id) return { ok: false, error: 'threadId boş.' };
  var th = GmailApp.getThreadById(id);
  if (!th) return { ok: false, error: 'Yazışma bulunamadı: ' + id };

  var allMsgs = th.getMessages();
  var recent = allMsgs.length > 5 ? allMsgs.slice(allMsgs.length - 5) : allMsgs;
  var remaining = MAX_GMAIL_READ_CHARS;
  var perMsgCap = Math.max(400, Math.floor(MAX_GMAIL_READ_CHARS / Math.max(recent.length, 1)));
  var messages = [];

  for (var i = 0; i < recent.length; i++) {
    var m = recent[i];
    var plain = String(m.getPlainBody() || '').trim();
    var allowed = Math.min(remaining, Math.max(perMsgCap, remaining - (recent.length - 1 - i) * 300));
    var bodyText = plain;
    var truncated = false;
    if (allowed <= 0) {
      bodyText = '…(uzunluk sınırına ulaşıldı)';
      truncated = true;
    } else if (plain.length > allowed) {
      bodyText = plain.slice(0, allowed) + '\n…(kısaltıldı)';
      truncated = true;
    }
    remaining = Math.max(0, remaining - Math.min(plain.length, Math.max(0, allowed)));

    var rawAtts = m.getAttachments({ includeInlineImages: false, includeAttachments: true });
    var attachments = [];
    for (var j = 0; j < rawAtts.length; j++) {
      var att = rawAtts[j];
      attachments.push({
        name: att.getName(),
        contentType: att.getContentType(),
        size: att.getSize(),
      });
    }

    messages.push({
      from: m.getFrom(),
      to: m.getTo(),
      date: formatDateTr_(m.getDate()),
      subject: m.getSubject(),
      body: bodyText,
      truncated: truncated,
      attachments: attachments,
    });
  }

  return {
    ok: true,
    threadId: th.getId(),
    subject: th.getFirstMessageSubject() || '',
    messageCount: allMsgs.length,
    messages: messages,
  };
}

// Güvenlik sınırı: yalnızca taslak oluşturulur; sendEmail ve silme bilerek yoktur.
function handleGmailDraft_(to, subject, body) {
  var recipient = String(to || '').trim();
  if (!recipient) return { ok: false, error: 'Alıcı (to) boş.' };
  var subj = String(subject || '').trim();
  var text = String(body || '');
  var draft = GmailApp.createDraft(recipient, subj, text);
  return {
    ok: true,
    draftId: draft.getId(),
    to: recipient,
    subject: subj,
  };
}

function formatDateTr_(d) {
  if (!d) return '';
  try {
    return Utilities.formatDate(d, 'Europe/Istanbul', 'yyyy-MM-dd HH:mm');
  } catch (ignore) {
    return d.toISOString ? d.toISOString() : String(d);
  }
}

function getOrCreateFolder_() {
  var it = DriveApp.getFoldersByName(FOLDER_NAME);
  if (it.hasNext()) return it.next();
  return DriveApp.createFolder(FOLDER_NAME);
}

function getOrCreateTextFile_(folder, name, initialContent) {
  var it = folder.getFilesByName(name);
  if (it.hasNext()) return it.next();
  return folder.createFile(name, initialContent, MimeType.PLAIN_TEXT);
}

function jsonOut_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
