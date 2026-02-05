package dev.latvian.mods.literalskyblock.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.latvian.mods.literalskyblock.LiteralSkyBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PhoneQrCodeManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation PHONE_NUMBERS = ResourceLocation.fromNamespaceAndPath(LiteralSkyBlock.MOD_ID, "phone_numbers.json");
	private static final int QR_SIZE = 64;
	private static final int QR_MARGIN = 1;
	private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
	private static List<QrCodeEntry> cachedCodes;

	public record QrCodeEntry(String phoneNumber, ResourceLocation texture, int size) {
	}

	private PhoneQrCodeManager() {
	}

	public static List<QrCodeEntry> getQrCodes() {
		if (cachedCodes == null) {
			cachedCodes = loadQrCodes();
		}

		return cachedCodes;
	}

	private static List<QrCodeEntry> loadQrCodes() {
		List<String> numbers = loadPhoneNumbers();
		List<QrCodeEntry> codes = new ArrayList<>();

		for (String number : numbers) {
			if (number.isBlank()) {
				continue;
			}

			String normalized = number.replaceAll("\\s+", "");
			ResourceLocation texture = TEXTURE_CACHE.computeIfAbsent(normalized, PhoneQrCodeManager::createQrTexture);

			if (texture != null) {
				codes.add(new QrCodeEntry(number, texture, QR_SIZE));
			}
		}

		return codes;
	}

	private static List<String> loadPhoneNumbers() {
		Minecraft mc = Minecraft.getInstance();
		List<String> numbers = new ArrayList<>();

		try (var resource = mc.getResourceManager().open(PHONE_NUMBERS);
			 var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
			JsonElement json = JsonParser.parseReader(reader);

			if (json.isJsonArray()) {
				JsonArray array = json.getAsJsonArray();
				for (JsonElement entry : array) {
					if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
						numbers.add(entry.getAsString());
					}
				}
			}
		} catch (IOException e) {
			LOGGER.debug("No phone numbers resource found for QR codes: {}", PHONE_NUMBERS, e);
		}

		return numbers;
	}

	private static ResourceLocation createQrTexture(String number) {
		Minecraft mc = Minecraft.getInstance();
		QRCodeWriter writer = new QRCodeWriter();
		Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, QR_MARGIN);

		try {
			BitMatrix matrix = writer.encode("tel:" + number, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
			NativeImage image = new NativeImage(QR_SIZE, QR_SIZE, false);

			for (int y = 0; y < QR_SIZE; y++) {
				for (int x = 0; x < QR_SIZE; x++) {
					image.setPixelRGBA(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
				}
			}

			DynamicTexture texture = new DynamicTexture(image);
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath(LiteralSkyBlock.MOD_ID, "qr/" + Integer.toHexString(number.hashCode()));
			mc.getTextureManager().register(location, texture);
			return location;
		} catch (WriterException e) {
			LOGGER.warn("Failed to generate QR code for phone number {}", number, e);
			return null;
		}
	}
}
