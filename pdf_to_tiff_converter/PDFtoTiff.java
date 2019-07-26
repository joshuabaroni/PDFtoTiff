package com.adp.pdf_to_tiff_converter;

/** Copyright 2006-2012 ICEsoft Technologies Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the
* License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
* governing permissions and limitations under the License.
*
* @author Edited by Joshua Baroni (ADP GPT/GETS Intern, Summer 2019) : June 7, 2019
* @author earmanw	Edited by Will Earman ADP GPT/GETS Intern Summer 2019. Modularized the java file + Bugfixes
*/
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.util.GraphicsRenderingHints;

import com.github.jaiimageio.plugins.tiff.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.DataBuffer;
import java.awt.*;
import java.io.*;
import java.util.Iterator;

/**
 * The <code>MultiPageCapture</code> class is an example of how to save page
 * captures to disk. A PDF, specified at the command line, is opened, and every
 * page in the document is captured as an image and saved into one multi-page
 * group 4 fax TIFF graphics file.
 *
 * @since 4.0
 */
public class PDFtoTiff {
	private static final String FILETYPE = ".tif";
	public static final int WHITE_HEX = 0xFFFFFFFF, BLACK_HEX = 0xFF000000;

	private static String FILENAME;// = "Quote-_01-2019-1024634";
	private static String FOLDER_PATH;// = "C:/Users/earmanw/Pictures/OCR Test Files/";
	public static double FAX_RESOLUTION = 400.0;
	public static double PRINTER_RESOLUTION = 600.0;
	// This compression type may be specific to JAI ImageIO Tools
	public static String COMPRESSION_TYPE_GROUP4FAX = "CCITT T.6";
	private static double dpi;
	private static float scale = 2.0f, rotation = 0f;

	/**
	 * Main method of MultiPageCapture, calls private methods to convert a .PDF
	 * document to .TIFF
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		FILENAME = args[0];
		FOLDER_PATH = args[1];
		FAX_RESOLUTION = Double.parseDouble(args[2]);
		PRINTER_RESOLUTION = Double.parseDouble(args[3]);
		// System.out.println(FAX_RESOLUTION + "\n" + PRINTER_RESOLUTION);
		// COMPRESSION_TYPE_GROUP4FAX = args[4];
		// scale = Float.parseFloat(args[5]);
		// rotation = Float.parseFloat(args[6]);

		verifyImageIO();
		try {
			startTransfer();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Verify that the ImageIO in use can output a .tiff file
	 */
	private static void verifyImageIO() {
		// Verify that ImageIO can output TIFF
		Iterator<ImageWriter> iterator = ImageIO.getImageWritersByFormatName("tiff");
		if (!iterator.hasNext()) {
			System.out.println("ImageIO missing required plug-in to write TIFF files. "
					+ "You can download the JAI ImageIO Tools from: " + "https://jai-imageio.dev.java.net/");
			return;
		}
		boolean foundCompressionType = false;
		for (String type : iterator.next().getDefaultWriteParam().getCompressionTypes()) {
			if (COMPRESSION_TYPE_GROUP4FAX.equals(type)) {
				foundCompressionType = true;
				break;
			}
		}
		if (!foundCompressionType) {
			System.out.println("TIFF ImageIO plug-in does not support Group 4 Fax " + "compression type ("
					+ COMPRESSION_TYPE_GROUP4FAX + ")");
			return;
		}
	}

	/**
	 * Begin conversion process, calls pdf checking method then creates a new file
	 * which will become the .tif pdf will be printed to the tiff document page by
	 * page with another method
	 *
	 * @throws InterruptedException
	 */
	private static void startTransfer() throws InterruptedException {
		// Get a file to open

		String filePath = FOLDER_PATH + FILENAME;
		// open the url
		Document document = new Document();
		pdfTroubleshooting(document, filePath);

		try {
			// save page captures to file.
			File file = new File(filePath + ".tif");
			ImageOutputStream ios = ImageIO.createImageOutputStream(file);
			ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next(); // TODO
			writer.setOutput(ios);
			printPageContent(document, writer);
			ios.flush();
			ios.close();
			writer.dispose();
			System.out.println(FILENAME + FILETYPE + " has been successfully processed");
		} catch (IIOInvalidTreeException e) {
			System.out.println("Error: Metadata creation Failed. " + e);
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Error saving file " + e);
			e.printStackTrace();
		}
		// clean up resources
		document.dispose();
	}

	/**
	 * Used to see if any errors occur when trying to set the document in use to a
	 * .PDF
	 *
	 * @param document of type Document
	 * @param filePath String for path of file up for conversion
	 */
	private static void pdfTroubleshooting(Document document, String filePath) {
		try {
			document.setFile(filePath + ".pdf");
		} catch (PDFException ex) {
			System.out.println("Error parsing PDF document " + ex);
		} catch (PDFSecurityException ex) {
			System.out.println("Error encryption not supported " + ex);
		} catch (FileNotFoundException ex) {
			System.out.println("Error file not found " + ex);
		} catch (IOException ex) {
			System.out.println("Error handling PDF document " + ex);
		}
	}

	/**
	 * Goes page by page of the .pdf, turns all color to either black or white, then
	 * writes to the .tif file
	 *
	 * @param document of type Document. The pdf which we are converting
	 * @param writer   ImageWriter which allows us to write each page into the tif
	 *                 file
	 * @throws IIOInvalidTreeException Error thrown when there are issues with
	 *                                 creating metadata
	 * @throws IOException             General Exception thrown when dealing with
	 *                                 reading/writing files.
	 * @throws InterruptedException
	 */
	@SuppressWarnings("static-access")
	private static void printPageContent(Document document, ImageWriter writer)
			throws IIOInvalidTreeException, IOException, InterruptedException {
		// Paint each pages content to an image and write the image to file
		for (int i = 0; i < document.getNumberOfPages(); i++) {
			PDimension size = dpiCalc(document, i);
			int pageWidth = (int) size.getWidth();
			int pageHeight = (int) size.getHeight();
			// {Lower color limit, Upper color limit}
			int[] cmap = new int[] { BLACK_HEX, WHITE_HEX };
			IndexColorModel cm = new IndexColorModel(1, cmap.length, cmap, 0, false, Transparency.BITMASK,
					DataBuffer.TYPE_BYTE);
			BufferedImage image = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_BYTE_BINARY, cm);
			Graphics g = image.createGraphics();
			document.paintPage(i, g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, rotation, scale);
			g.dispose();
			// capture the page image to file
			IIOImage img = new IIOImage(image, null, createMetadata(writer, writer.getDefaultWriteParam(), (int) dpi));
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(param.MODE_EXPLICIT);
			param.setCompressionType(COMPRESSION_TYPE_GROUP4FAX);
			if (i == 0) {
				writer.write(null, img, param);
			} else {
				writer.writeInsert(-1, img, param);
			}
			image.flush();
//			System.out.println("Page " + (i + 1) + " completed");
		}
	}

	/**
	 * Calculates the DPI of the image (needed for better file conversion)
	 *
	 * @param document Document, pdf which we are using
	 * @param i        Int of page number we are on
	 * @return size PDimension used to calc page width and height in other method
	 */
	private static PDimension dpiCalc(Document document, int i) {
		final double targetDPI = PRINTER_RESOLUTION;

		// Given no initial zooming, calculate our natural DPI when
		// printed to standard US Letter paper
		PDimension size = document.getPageDimension(i, rotation, scale);
		dpi = Math.sqrt((size.getWidth() * size.getWidth()) + (size.getHeight() * size.getHeight()))
				/ Math.sqrt((8.5 * 8.5) + (11 * 11));
		// Calculate scale required to achieve at least our target DPI
		if (dpi < (targetDPI - 0.1)) {
			scale = (float) (targetDPI / dpi);
			size = document.getPageDimension(i, rotation, scale);
		}
		return size;
	}

	/**
	 * Creates metaData for tif File.
	 *
	 * @param writer       ImageWriter being used to write to tiff
	 * @param writerParams ImageWriterParam
	 * @param resolution   Int resolution of the pdf/tiff
	 * @return IIOMetadata Metadata object
	 * @throws IIOInvalidTreeException Exception for when metadata goes wrong
	 */
	private static IIOMetadata createMetadata(ImageWriter writer, ImageWriteParam writerParams, int resolution)
			throws IIOInvalidTreeException {
		// Get default metadata from writer
		ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_BYTE_GRAY);
		IIOMetadata meta = writer.getDefaultImageMetadata(type, writerParams);

		// Convert default metadata to TIFF metadata
		TIFFDirectory dir = TIFFDirectory
				.createFromMetadata(meta);

		// Get {X,Y} resolution tags
		BaselineTIFFTagSet base = BaselineTIFFTagSet
				.getInstance();
		TIFFTag tagXRes = base
				.getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION);
		TIFFTag tagYRes = base
				.getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION);

		// Create {X,Y} resolution fields
		TIFFField fieldXRes = new TIFFField(
				tagXRes, TIFFTag.TIFF_RATIONAL, 1,
				new long[][] { { resolution, 1 } });
		TIFFField fieldYRes = new TIFFField(
				tagYRes, TIFFTag.TIFF_RATIONAL, 1,
				new long[][] { { resolution, 1 } });

		// Add {X,Y} resolution fields to TIFFDirectory
		dir.addTIFFField(fieldXRes);
		dir.addTIFFField(fieldYRes);

		// Add unit field to TIFFDirectory (change to RESOLUTION_UNIT_CENTIMETER if
		// necessary)
		dir.addTIFFField(new TIFFField(
				base.getTag(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT),
				BaselineTIFFTagSet.RESOLUTION_UNIT_INCH));

		// Return TIFF metadata so it can be picked up by the IIOImage
		return dir.getAsMetadata();
	}
}
//import java.awt.image.*;
//import java.io.*;
//import javax.imageio.ImageIO;
//import org.icepdf.core.exceptions.*;
//import org.icepdf.core.pobjects.*;
//import org.icepdf.core.util.GraphicsRenderingHints;
//public class PDFToTiff {
//    public static void main(String[] args) throws InterruptedException {
//		final String FILENAME = args[0] + ".pdf";
//		final String FOLDER_PATH = args[1];
//		final double FAX_RESOLUTION = Double.parseDouble(args[2]);
//		final double PRINTER_RESOLUTION = Double.parseDouble(args[3]);
//          Document document = new Document();
//          try {
//             document.setFile(FOLDER_PATH + FILENAME);
//          } catch (PDFException ex) {
//             System.out.println("Error parsing PDF document " + ex);
//          } catch (PDFSecurityException ex) {
//             System.out.println("Error encryption not supported " + ex);
//          } catch (FileNotFoundException ex) {
//             System.out.println("Error file not found " + ex);
//          } catch (IOException ex) {
//             System.out.println("Error IOException " + ex);
//          }
//          float scale = 1.0f;
//          float rotation = 0f;
//          for (int i = 0; i < document.getNumberOfPages(); i++) {
//             BufferedImage image = (BufferedImage) document.getPageImage(
//                 i, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, rotation, scale);
//             RenderedImage rendImage = image;
//             try {
//                System.out.println(" capturing page " + i);
//                File file = new File(FOLDER_PATH + FILENAME.substring(0, FILENAME.lastIndexOf(".")) + ".tif");
//                ImageIO.write(rendImage, "tiff", file);
//             } catch (IOException e) {
//                e.printStackTrace();
//             }
//             image.flush();
//          }
//          document.dispose();
//    }
//}