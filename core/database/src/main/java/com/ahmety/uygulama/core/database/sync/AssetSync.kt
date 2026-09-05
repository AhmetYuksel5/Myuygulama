package com.ahmety.uygulama.core.database.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Taşınan bir dosya parçası. */
private data class Part(
    val base: String,
    val index: Int,
    val total: Int,
    val name: String,
)

/**
 * Kitapların, PDF'lerin ve altyazı metinlerinin taşınması.
 *
 * Değişiklik günlüğü yalnızca veritabanı satırlarını taşıyor; kitaplığa
 * düşen kayıt karşı telefonda görünüyor ama dosyası orada olmadığı için
 * açılmıyordu. Kayıtların içinde dosyanın **mutlak yolu** yazıyor ve o yol
 * iki telefonda da aynı (aynı paket, aynı özel alan) — yani eksik olan tek
 * şey dosyanın kendisi.
 *
 * Dosyalar dört megabaytlık parçalara bölünüp taşınıyor. İki sebep:
 * yüz megabaytlık bir PDF'i tek seferde belleğe almak uygulamayı düşürür,
 * ve GitHub yolunda tek bir devasa istek yerine sindirilebilir parçalar
 * gidiyor. Parça adı kaçıncı parça olduğunu ve kaç parça olduğunu
 * taşıdığı için karşı taraf eksik dosyayı yarım yazmıyor.
 *
 * Her parça ayrı ayrı şifreleniyor; taşıyıcı yine anlamsız baytlar
 * görüyor.
 */
@Singleton
class AssetSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: SyncCrypto,
    private val transport: ActiveTransport,
    @DeviceId private val deviceId: String,
) {

    private val myFolder = deviceId + ASSET_SUFFIX

    /** @return gönderilen parça sayısı */
    suspend fun push(): Int = withContext(Dispatchers.IO) {
        var sent = 0

        // Yalnız kendi klasörümüze değil **bütün** dosya klasörlerine
        // bakılıyor.
        //
        // Karşı telefondan gelen kitap bizde de aynı klasöre yazılıyor ve
        // yalnız kendi klasörümüze bakılınca "bunu hiç göndermemişim"
        // sanılıyordu: ikinci telefon aldığı bütün PDF'leri kendi adına
        // geri yüklüyordu. Hem her senkron dakikalar sürüyordu hem de
        // deponun içinde her kitabın iki kopyası birikiyordu.
        val already = runCatching { transport.deviceFolders() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(ASSET_SUFFIX) }
            .plus(myFolder)
            .distinct()
            .flatMap { folder ->
                runCatching { transport.fileNames(folder) }.getOrDefault(emptyList())
            }
            .toSet()

        FOLDERS.forEach { folder ->
            val directory = File(context.filesDir, folder)
            directory.listFiles()?.filter { it.isFile }?.forEach { file ->
                if (!safe(file.name)) return@forEach
                val total = ((file.length() + CHUNK - 1) / CHUNK).toInt().coerceAtLeast(1)
                val names = (0 until total).map { partName(folder, file.name, it, total) }
                // Bütün parçaları zaten göndermişsek dosyayı hiç açmıyoruz.
                if (names.all { it in already }) return@forEach

                runCatching {
                    file.inputStream().use { input ->
                        val buffer = ByteArray(CHUNK)
                        for (index in 0 until total) {
                            var read = 0
                            while (read < CHUNK) {
                                val count = input.read(buffer, read, CHUNK - read)
                                if (count < 0) break
                                read += count
                            }
                            if (read <= 0) break
                            val name = names[index]
                            if (name in already) continue
                            val payload = crypto.encrypt(buffer.copyOf(read))
                            if (transport.write(myFolder, name, payload)) sent++
                        }
                    }
                }
            }
        }
        sent
    }

    /** @return alınan dosya sayısı */
    suspend fun pull(): Int = withContext(Dispatchers.IO) {
        var received = 0
        val remotes = runCatching { transport.deviceFolders() }.getOrDefault(emptyList())
            .filter { it.endsWith(ASSET_SUFFIX) && it != myFolder }

        remotes.forEach { remote ->
            val parts = runCatching { transport.fileNames(remote) }.getOrDefault(emptyList())
                .mapNotNull { parsePart(it) }
                .groupBy { it.base }

            parts.forEach { (base, pieces) ->
                val total = pieces.first().total
                // Eksik parçalı dosyaya hiç dokunmuyoruz; karşı telefon
                // gönderimi bitirmemiş olabilir, bir sonraki turda gelir.
                if (pieces.distinctBy { it.index }.size < total) return@forEach

                val folder = base.substringBefore(PATH_SEPARATOR, "")
                val fileName = base.substringAfter(PATH_SEPARATOR, "")
                if (folder !in FOLDERS || !safe(fileName)) return@forEach

                val directory = File(context.filesDir, folder)
                if (!directory.isDirectory && !directory.mkdirs()) return@forEach
                val target = File(directory, fileName)
                if (target.exists()) return@forEach

                // Önce geçici bir ada yazılıyor: yarıda kalan bir indirme
                // yarım bir kitap olarak kitaplıkta görünmesin.
                val temporary = File(directory, "$fileName$PENDING")
                val complete = runCatching {
                    temporary.outputStream().use { output ->
                        for (index in 0 until total) {
                            val piece = pieces.firstOrNull { it.index == index }
                                ?: return@runCatching false
                            val bytes = transport.read(remote, piece.name)
                                ?: return@runCatching false
                            val plain = crypto.decrypt(bytes) ?: return@runCatching false
                            output.write(plain)
                        }
                    }
                    true
                }.getOrDefault(false)

                if (complete && temporary.renameTo(target)) received++ else temporary.delete()
            }
        }
        received
    }

    private fun partName(folder: String, name: String, index: Int, total: Int): String =
        "%s%s%s%s%04d_%04d".format(
            folder,
            PATH_SEPARATOR,
            name,
            PART_SEPARATOR,
            index,
            total,
        )

    private fun parsePart(name: String): Part? {
        val marker = name.lastIndexOf(PART_SEPARATOR)
        if (marker <= 0) return null
        val tail = name.substring(marker + PART_SEPARATOR.length).split('_')
        if (tail.size != 2) return null
        val index = tail[0].toIntOrNull() ?: return null
        val total = tail[1].toIntOrNull() ?: return null
        if (total <= 0 || index < 0 || index >= total) return null
        return Part(base = name.take(marker), index = index, total = total, name = name)
    }

    /** Ad denetimi: yol dışına çıkan bir ad başka bir yere yazabilirdi. */
    private fun safe(name: String): Boolean =
        name.isNotBlank() &&
            name.none { it == '/' || it == '\\' } &&
            !name.contains(PATH_SEPARATOR) &&
            !name.contains(PART_SEPARATOR) &&
            name != "." &&
            name != ".."

    companion object {
        /**
         * Dosya taşıyan cihaz klasörünün soneki.
         *
         * Değişiklik günlüğü klasörlerinden ayrı duruyor; senkron motoru
         * bu klasörlere bakmıyor.
         */
        const val ASSET_SUFFIX = "-dosya"

        /**
         * Taşınan klasörler. Bugün yalnızca kitaplar: EPUB'lar, PDF'ler,
         * çıkarılmış metinler ve altyazılardan üretilen metinler burada.
         */
        private val FOLDERS = listOf("kitaplar")

        private const val CHUNK = 4 * 1024 * 1024
        private const val PATH_SEPARATOR = "~"
        private const val PART_SEPARATOR = "__"
        private const val PENDING = ".yariM"
    }
}
