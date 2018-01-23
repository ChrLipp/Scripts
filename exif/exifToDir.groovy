@Grapes([
    @Grab(group='com.drewnoakes', module='metadata-extractor', version='2.6.2')
])

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import static groovy.io.FileType.FILES

class ExifMover
{
	final String BASE_DIR = '/Users/Christian/Pictures/Christians iPhone'
	final boolean isProduction = true

	void move()
	{
		new File(BASE_DIR).eachFile(FILES) { file ->
			if (file.toString().toUpperCase().endsWith('.JPG')) {

				// Datum des Fotos aus EXIF Daten einlesen
				String date = null
				try {
					date = ImageMetadataReader
						.readMetadata(file)
						.getDirectory(ExifSubIFDDirectory.class)
						.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
						.format('yyyyMM')
//						.format('yyyyMMdd')
				}
				catch (Exception e) {
					return
				}

				// Verzeichnis erzeugen, wenn es noch nicht existiert
				def dir = new File("${BASE_DIR}${File.separator}${date}")
				makeDirectory(dir)

				// Foto in das Verzeichnis verschieben
				def destination = new File("${dir.absolutePath}${File.separator}${file.name}")
				moveFile(file, destination)
			}
		}
	}

	void makeDirectory(File directory)
	{
		if (isProduction) {
			if (!directory.exists()) {
				directory.mkdir()
			}
		}
		else {
			println "Make directory ${directory.path}"
		}
	}

	void moveFile(File source, File destination)
	{
		if (isProduction) {
			java.nio.file.Files.move(source.toPath(), destination.toPath())
		}
		else {
			println "Move file from ${source.path} to ${destination.path}"
		}
	}
}

def mover = new ExifMover()
mover.move()
