package com.boothly.app.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

/**
 * Document / business-card detection, perspective correction and enhancement
 * (same idea as FairScan: find the page edges, straighten it, clean it up).
 * Everything runs offline with OpenCV.
 */
object DocDetector {
    @Volatile
    private var ready = false

    fun init(): Boolean {
        if (ready) return true
        ready = try {
            OpenCVLoader.initLocal()
        } catch (_: Throwable) {
            try {
                System.loadLibrary("opencv_java4")
                true
            } catch (_: Throwable) {
                false
            }
        }
        return ready
    }

    /** Rotate (by camera rotation) and shrink a frame so its long side is [maxSide]. */
    fun prepare(src: Bitmap, rotation: Int, maxSide: Int): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / max(src.width, src.height))
        if (scale >= 1f && rotation == 0) return src
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0) m.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Finds the biggest 4-corner shape (card / page).
     * Returns 4 corners normalised to 0..1, ordered top-left, top-right, bottom-right, bottom-left.
     */
    fun detect(bmp: Bitmap): List<PointF>? {
        if (!init()) return null
        val w = bmp.width.toDouble()
        val h = bmp.height.toDouble()
        val rgba = Mat()
        val gray = Mat()
        val edges = Mat()
        val hier = Mat()
        try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            // Try a few edge sensitivities: strong edges first, then weaker (light card on light table)
            for ((lo, hi) in listOf(60.0 to 160.0, 30.0 to 90.0, 15.0 to 50.0)) {
                Imgproc.Canny(gray, edges, lo, hi)
                val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, k)
                k.release()
                val contours = ArrayList<MatOfPoint>()
                Imgproc.findContours(edges, contours, hier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                val best = bestQuad(contours, w * h)
                contours.forEach { it.release() }
                if (best != null) {
                    return order(best).map { PointF((it.x / w).toFloat(), (it.y / h).toFloat()) }
                }
            }
            return null
        } catch (_: Throwable) {
            return null
        } finally {
            rgba.release(); gray.release(); edges.release(); hier.release()
        }
    }

    private fun bestQuad(contours: List<MatOfPoint>, imgArea: Double): Array<Point>? {
        var best: Array<Point>? = null
        var bestArea = 0.0
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < imgArea * 0.10 || area > imgArea * 0.98) continue
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.025 * peri, true)
            var pts: Array<Point>? = null
            if (approx.total() == 4L) {
                val mp = MatOfPoint(*approx.toArray())
                if (Imgproc.isContourConvex(mp)) pts = approx.toArray()
                mp.release()
            }
            if (pts == null && area > imgArea * 0.20) {
                // rounded corners / small gaps: use the rotated bounding box if it fits the shape well
                val rect = Imgproc.minAreaRect(c2f)
                if (area / (rect.size.width * rect.size.height) > 0.85) {
                    val box = arrayOfNulls<Point>(4)
                    rect.points(box)
                    pts = box.map { it ?: Point() }.toTypedArray()
                }
            }
            if (pts != null && area > bestArea) {
                best = pts
                bestArea = area
            }
            c2f.release(); approx.release()
        }
        return best
    }

    private fun order(p: Array<Point>): List<Point> {
        val tl = p.minByOrNull { it.x + it.y }!!
        val br = p.maxByOrNull { it.x + it.y }!!
        val tr = p.minByOrNull { it.y - it.x }!!
        val bl = p.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }

    /** Straighten the card using its 4 corners (normalised 0..1) and return the cropped, enhanced image. */
    fun warp(src: Bitmap, quad: List<PointF>): Bitmap {
        init()
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        val p = quad.map { Point(it.x * w, it.y * h) }
        val (tl, tr, br, bl) = p
        val outW = max(dist(tl, tr), dist(bl, br)).coerceAtLeast(50.0)
        val outH = max(dist(tl, bl), dist(tr, br)).coerceAtLeast(50.0)

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(Point(0.0, 0.0), Point(outW - 1, 0.0), Point(outW - 1, outH - 1), Point(0.0, outH - 1))
        val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val input = Mat()
        val out = Mat()
        Utils.bitmapToMat(src, input)
        Imgproc.warpPerspective(input, out, m, Size(outW, outH), Imgproc.INTER_CUBIC)
        enhanceMat(out)
        val result = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, result)
        srcPts.release(); dstPts.release(); m.release(); input.release(); out.release()
        return result
    }

    /** Enhance a whole picture without cropping. */
    fun enhance(src: Bitmap): Bitmap {
        if (!init()) return src
        val mat = Mat()
        Utils.bitmapToMat(src, mat)
        enhanceMat(mat)
        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, result)
        mat.release()
        return result
    }

    /** Even out lighting & boost local contrast (keeps colours), then sharpen a little for better OCR. */
    private fun enhanceMat(rgba: Mat) {
        val rgb = Mat()
        val lab = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        val ch = ArrayList<Mat>()
        Core.split(lab, ch)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(ch[0], ch[0])
        Core.merge(ch, lab)
        Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)

        val blur = Mat()
        Imgproc.GaussianBlur(rgb, blur, Size(0.0, 0.0), 2.0)
        Core.addWeighted(rgb, 1.5, blur, -0.5, 0.0, rgb)
        rgb.convertTo(rgb, CvType.CV_8UC3)
        Imgproc.cvtColor(rgb, rgba, Imgproc.COLOR_RGB2RGBA)

        ch.forEach { it.release() }
        rgb.release(); lab.release(); blur.release()
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
