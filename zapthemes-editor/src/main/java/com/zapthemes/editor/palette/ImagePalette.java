package com.zapthemes.editor.palette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;

/**
 * The dominant colors of an image ("From image" on the palette page): k-means clustering of its
 * pixels in OKLab, where distances match what the eye sees. Seeded (k-means++ start), so the same
 * image and seed give the same colors.
 */
public final class ImagePalette {

    /** Pixels sampled at most; larger images are sampled on an even grid. */
    static final int MAX_SAMPLES = 20_000;
    private static final int MAX_ITERATIONS = 50;
    private static final int OPAQUE = 128;

    private ImagePalette() {
    }

    /**
     * Up to {@code k} colors of {@code image}, the most common first. Transparent pixels (alpha
     * below half) are left out; an image with fewer distinct colors gives fewer.
     */
    public static List<RGB> extract(ImageData image, int k, long seed) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }
        List<double[]> points = sample(image);
        if (points.isEmpty()) {
            return List.of();
        }
        Random random = new Random(seed);
        List<double[]> centers = start(points, k, random);
        int[] assignment = new int[points.size()];
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean changed = false;
            for (int i = 0; i < points.size(); i++) {
                int nearest = nearest(points.get(i), centers);
                if (nearest != assignment[i]) {
                    assignment[i] = nearest;
                    changed = true;
                }
            }
            if (iteration > 0 && !changed) {
                break;
            }
            centers = means(points, assignment, centers);
        }
        int[] counts = new int[centers.size()];
        for (int a : assignment) {
            counts[a]++;
        }
        List<Integer> order = new ArrayList<>();
        for (int c = 0; c < centers.size(); c++) {
            if (counts[c] > 0) {
                order.add(c);
            }
        }
        order.sort(Comparator.comparingInt((Integer c) -> counts[c]).reversed());
        List<RGB> colors = new ArrayList<>();
        for (int c : order) {
            RGB rgb = Oklch.fromOklab(centers.get(c));
            if (!colors.contains(rgb)) {
                colors.add(rgb);
            }
        }
        return colors;
    }

    private static List<double[]> sample(ImageData image) {
        long total = (long) image.width * image.height;
        int stride = (int) Math.max(1, Math.ceil(Math.sqrt((double) total / MAX_SAMPLES)));
        List<double[]> points = new ArrayList<>();
        for (int y = 0; y < image.height; y += stride) {
            for (int x = 0; x < image.width; x += stride) {
                if (image.getAlpha(x, y) < OPAQUE || isTransparentPixel(image, x, y)) {
                    continue;
                }
                RGB rgb = image.palette.getRGB(image.getPixel(x, y));
                points.add(Oklch.oklab(rgb));
            }
        }
        return points;
    }

    private static boolean isTransparentPixel(ImageData image, int x, int y) {
        return image.transparentPixel != -1 && image.getPixel(x, y) == image.transparentPixel;
    }

    /** k-means++: each next center is drawn with probability proportional to its squared distance. */
    private static List<double[]> start(List<double[]> points, int k, Random random) {
        List<double[]> centers = new ArrayList<>();
        centers.add(points.get(random.nextInt(points.size())).clone());
        double[] distances = new double[points.size()];
        while (centers.size() < k) {
            double sum = 0;
            for (int i = 0; i < points.size(); i++) {
                double d = squared(points.get(i), centers.get(nearest(points.get(i), centers)));
                distances[i] = d;
                sum += d;
            }
            if (sum < 1e-12) {
                break;
            }
            double target = random.nextDouble() * sum;
            int chosen = points.size() - 1;
            for (int i = 0; i < points.size(); i++) {
                target -= distances[i];
                if (target <= 0) {
                    chosen = i;
                    break;
                }
            }
            centers.add(points.get(chosen).clone());
        }
        return centers;
    }

    private static List<double[]> means(List<double[]> points, int[] assignment, List<double[]> centers) {
        double[][] sums = new double[centers.size()][3];
        int[] counts = new int[centers.size()];
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            double[] s = sums[assignment[i]];
            s[0] += p[0];
            s[1] += p[1];
            s[2] += p[2];
            counts[assignment[i]]++;
        }
        List<double[]> moved = new ArrayList<>();
        for (int c = 0; c < centers.size(); c++) {
            moved.add(counts[c] == 0 ? centers.get(c)
                    : new double[] { sums[c][0] / counts[c], sums[c][1] / counts[c], sums[c][2] / counts[c] });
        }
        return moved;
    }

    private static int nearest(double[] point, List<double[]> centers) {
        int best = 0;
        double distance = Double.MAX_VALUE;
        for (int c = 0; c < centers.size(); c++) {
            double d = squared(point, centers.get(c));
            if (d < distance) {
                distance = d;
                best = c;
            }
        }
        return best;
    }

    private static double squared(double[] a, double[] b) {
        double x = a[0] - b[0];
        double y = a[1] - b[1];
        double z = a[2] - b[2];
        return x * x + y * y + z * z;
    }
}
