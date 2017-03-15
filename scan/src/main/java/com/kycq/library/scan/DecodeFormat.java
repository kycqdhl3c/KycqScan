package com.kycq.library.scan;

import com.google.zxing.BarcodeFormat;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class DecodeFormat {
	public static final String MODE = "SCAN_MODE";
	private static final String ONE_D_MODE = "ONE_D_MODE";
	private static final String PRODUCT_MODE = "PRODUCT_MODE";
	private static final String QR_CODE_MODE = "QR_CODE_MODE";
	private static final String DATA_MATRIX_MODE = "DATA_MATRIX_MODE";
	private static final String AZTEC_MODE = "AZTEC_MODE";
	private static final String PDF417_MODE = "PDF417_MODE";
	
	public static final String FORMATS = "SCAN_FORMATS";
	
	private static final Pattern COMMA_PATTERN = Pattern.compile(",");
	
	static final Set<BarcodeFormat> PRODUCT_FORMATS;
	static final Set<BarcodeFormat> INDUSTRIAL_FORMATS;
	private static final Set<BarcodeFormat> ONE_D_FORMATS;
	static final Set<BarcodeFormat> QR_CODE_FORMATS = EnumSet.of(BarcodeFormat.QR_CODE);
	static final Set<BarcodeFormat> DATA_MATRIX_FORMATS = EnumSet.of(BarcodeFormat.DATA_MATRIX);
	static final Set<BarcodeFormat> AZTEC_FORMATS = EnumSet.of(BarcodeFormat.AZTEC);
	static final Set<BarcodeFormat> PDF417_FORMATS = EnumSet.of(BarcodeFormat.PDF_417);
	
	static {
		PRODUCT_FORMATS = EnumSet.of(BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.RSS_14, BarcodeFormat.RSS_EXPANDED);
		INDUSTRIAL_FORMATS = EnumSet.of(BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODE_128, BarcodeFormat.ITF, BarcodeFormat.CODABAR);
		ONE_D_FORMATS = EnumSet.copyOf(PRODUCT_FORMATS);
		ONE_D_FORMATS.addAll(INDUSTRIAL_FORMATS);
	}
	
	private static final Map<String, Set<BarcodeFormat>> FORMATS_FOR_MODE;
	
	static {
		FORMATS_FOR_MODE = new HashMap<>();
		FORMATS_FOR_MODE.put(ONE_D_MODE, ONE_D_FORMATS);
		FORMATS_FOR_MODE.put(PRODUCT_MODE, PRODUCT_FORMATS);
		FORMATS_FOR_MODE.put(QR_CODE_MODE, QR_CODE_FORMATS);
		FORMATS_FOR_MODE.put(DATA_MATRIX_MODE, DATA_MATRIX_FORMATS);
		FORMATS_FOR_MODE.put(AZTEC_MODE, AZTEC_FORMATS);
		FORMATS_FOR_MODE.put(PDF417_MODE, PDF417_FORMATS);
	}
	
	private DecodeFormat() {
	}
}
