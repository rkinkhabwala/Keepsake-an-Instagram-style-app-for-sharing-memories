package com.keepsake.seed;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.ImageIO;

/** Draws simple, varied landscape "photos" for demo data, so the repository needs no binary sample images. */
final class SampleImages {

    private static final Color[][] SKIES = {
            {new Color(0x1E3A8A), new Color(0xF59E0B)},
            {new Color(0x0EA5E9), new Color(0xE0F2FE)},
            {new Color(0x4C1D95), new Color(0xF472B6)},
            {new Color(0x065F46), new Color(0xA7F3D0)},
            {new Color(0x7C2D12), new Color(0xFDBA74)},
            {new Color(0x0F172A), new Color(0x64748B)},
    };

    private SampleImages() {
    }

    static byte[] landscape(long seed, int width, int height) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color[] sky = SKIES[random.nextInt(SKIES.length)];
        graphics.setPaint(new GradientPaint(0, 0, sky[0], 0, height * 0.7f, sky[1]));
        graphics.fillRect(0, 0, width, height);

        int sun = height / 6 + random.nextInt(height / 8);
        graphics.setColor(new Color(255, 244, 214, 220));
        graphics.fillOval(random.nextInt(width - sun), height / 8 + random.nextInt(height / 4), sun, sun);

        for (int layer = 0; layer < 3; layer++) {
            float shade = 0.25f + layer * 0.2f;
            graphics.setColor(new Color(
                    clamp(sky[0].getRed() * shade), clamp(sky[0].getGreen() * shade + 40), clamp(sky[0].getBlue() * shade)));
            graphics.fillPolygon(ridge(random, width, height, height * (0.45 + layer * 0.15)));
        }

        graphics.setStroke(new BasicStroke(Math.max(2, width / 400f)));
        graphics.setColor(new Color(255, 255, 255, 60));
        for (int line = 0; line < 4; line++) {
            int y = (int) (height * (0.88 + line * 0.025));
            graphics.drawLine(random.nextInt(width / 3), y, width - random.nextInt(width / 3), y);
        }
        graphics.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Polygon ridge(Random random, int width, int height, double baseline) {
        Polygon polygon = new Polygon();
        polygon.addPoint(0, height);
        int points = 6 + random.nextInt(5);
        for (int point = 0; point <= points; point++) {
            int x = width * point / points;
            int y = (int) (baseline - random.nextDouble() * height * 0.18);
            polygon.addPoint(x, y);
        }
        polygon.addPoint(width, height);
        return polygon;
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }
}
