package astratto.tests;

import astratto.math.Mat4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelFrameTest {
    private static void rigid(Mat4 matrix) {
        float[] m = matrix.m;
        for (float value : m) assertTrue(Float.isFinite(value));
        for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
            float dot = 0;
            for (int row = 0; row < 3; row++) dot += m[a * 4 + row] * m[b * 4 + row];
            assertEquals(a == b ? 1 : 0, dot, 1e-4f, "no collapsed or skewed axes");
        }
        float det = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[4] * (m[1] * m[10] - m[2] * m[9]) + m[8] * (m[1] * m[6] - m[2] * m[5]);
        assertEquals(1, det, 1e-4f, "the frame must never mirror an item");
    }

    @Test void parallelAndZeroReferencesKeepTheItemRigidAtItsGrip() {
        float[] grip = {7, 8, 9};
        for (float[] axis : new float[][]{{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {0, 0, -1}})
            for (float[] ref : new float[][]{axis, {0, 0, 0}}) {
                Mat4 m = new Mat4().frame(grip, axis, ref);
                rigid(m);
                float[] tip = new float[3]; m.transformPoint(0, 1, 0, tip);
                for (int i = 0; i < 3; i++) assertEquals(grip[i] + axis[i], tip[i], 1e-4f);
            }
    }

    @Test void lookingAndTurningPreserveScaleHandednessAndBladeDirection() {
        Mat4 m = new Mat4();
        for (int yaw = -180; yaw <= 180; yaw += 5) for (int pitch = -90; pitch <= 90; pitch += 5) {
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            float[] axis = {(float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (-Math.cos(y) * Math.cos(p))};
            float[] ref = {(float) Math.cos(y), 0, (float) -Math.sin(y)};
            m.frame(new float[3], axis, ref); rigid(m);
            for (int i = 0; i < 3; i++) assertEquals(axis[i], m.m[4 + i], 1e-4f);
        }
    }
}
