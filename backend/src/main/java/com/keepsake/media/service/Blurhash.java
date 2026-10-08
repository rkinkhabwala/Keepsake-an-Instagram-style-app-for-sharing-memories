package com.keepsake.media.service;

import java.awt.image.BufferedImage;

/** Encoder for the BlurHash placeholder format (https://blurha.sh). */
public final class Blurhash {

    private static final String BASE83 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    private Blurhash() {
    }

    public static String encode(BufferedImage image, int componentsX, int componentsY) {
        if (componentsX < 1 || componentsX > 9 || componentsY < 1 || componentsY > 9) {
            throw new IllegalArgumentException("BlurHash components must be between 1 and 9");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        double[][] factors = new double[componentsX * componentsY][];
        for (int j = 0; j < componentsY; j++) {
            for (int i = 0; i < componentsX; i++) {
                factors[j * componentsX + i] = basisFactor(pixels, width, height, i, j);
            }
        }

        StringBuilder hash = new StringBuilder();
        encode83(hash, (componentsX - 1) + (componentsY - 1) * 9, 1);
        double maximumValue;
        if (factors.length > 1) {
            double actualMaximum = 0;
            for (int k = 1; k < factors.length; k++) {
                for (double component : factors[k]) {
                    actualMaximum = Math.max(actualMaximum, Math.abs(component));
                }
            }
            int quantisedMaximum = (int) Math.max(0, Math.min(82, Math.floor(actualMaximum * 166 - 0.5)));
            maximumValue = (quantisedMaximum + 1) / 166.0;
            encode83(hash, quantisedMaximum, 1);
        } else {
            maximumValue = 1;
            encode83(hash, 0, 1);
        }
        encode83(hash, encodeDc(factors[0]), 4);
        for (int k = 1; k < factors.length; k++) {
            encode83(hash, encodeAc(factors[k], maximumValue), 2);
        }
        return hash.toString();
    }

    private static double[] basisFactor(int[] pixels, int width, int height, int i, int j) {
        double r = 0;
        double g = 0;
        double b = 0;
        double normalisation = i == 0 && j == 0 ? 1 : 2;
        for (int y = 0; y < height; y++) {
            double cosY = Math.cos(Math.PI * j * y / height);
            for (int x = 0; x < width; x++) {
                double basis = normalisation * Math.cos(Math.PI * i * x / width) * cosY;
                int pixel = pixels[y * width + x];
                r += basis * srgbToLinear((pixel >> 16) & 0xFF);
                g += basis * srgbToLinear((pixel >> 8) & 0xFF);
                b += basis * srgbToLinear(pixel & 0xFF);
            }
        }
        double scale = 1.0 / (width * height);
        return new double[] {r * scale, g * scale, b * scale};
    }

    private static int encodeDc(double[] value) {
        return (linearToSrgb(value[0]) << 16) + (linearToSrgb(value[1]) << 8) + linearToSrgb(value[2]);
    }

    private static int encodeAc(double[] value, double maximumValue) {
        int r = quantiseAc(value[0] / maximumValue);
        int g = quantiseAc(value[1] / maximumValue);
        int b = quantiseAc(value[2] / maximumValue);
        return r * 19 * 19 + g * 19 + b;
    }

    private static int quantiseAc(double value) {
        return (int) Math.max(0, Math.min(18, Math.floor(Math.copySign(Math.sqrt(Math.abs(value)), value) * 9 + 9.5)));
    }

    private static double srgbToLinear(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int linearToSrgb(double value) {
        double v = Math.max(0, Math.min(1, value));
        double srgb = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) (srgb * 255 + 0.5);
    }

    private static void encode83(StringBuilder target, int value, int length) {
        for (int position = 1; position <= length; position++) {
            int digit = (value / (int) Math.pow(83, length - position)) % 83;
            target.append(BASE83.charAt(digit));
        }
    }
}
