import com.google.common.io.Files

@Grapes([
    @Grab(group='com.drewnoakes', module='metadata-extractor', version='2.6.2')
])

import static groovy.io.FileType.FILES

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory

final String BASE_DIR = 'D:\\Fotos'

new File(BASE_DIR).eachFile(FILES) { file ->

    // Datum des Fotos aus EXIF Daten einlesen
    String date = ImageMetadataReader
            .readMetadata(file)
            .getDirectory(ExifSubIFDDirectory.class)
            .getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
            .format('yyyyMMdd')

    // Verzeichnis erzeugen, wenn es noch nicht existiert
    def dir = new File("$BASE_DIR\\$date")
    if (!dir.exists())
    {
        dir.mkdir()
    }

    // Foto in das Verzeichnis verschieben
    def destination = new File("$dir.absolutePath\\$file.name")
    Files.move(file, destination)
}