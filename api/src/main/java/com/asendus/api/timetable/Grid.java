package com.asendus.api.timetable;

/**
 * Row and column geometry of one timetable page, derived from its own header digits and day labels.
 * Measurements: docs/superpowers/notes/2026-10-14-pdf-layout.md, section 3.
 */
final class Grid {

    static final int PERIODS = 7;
    static final int DAYS = 5;

    /** A day label's baseline sits 0.625 row heights below the row's top edge (notes §3). */
    private static final float DAY_LABEL_BASELINE = 0.625f;
    /** Slack for words drawn right up to the outer grid lines. */
    private static final float EDGE_TOLERANCE = 1f;

    private final float firstColCenter;
    private final float colWidth;
    private final float firstRowTop;
    private final float rowHeight;

    /** @param colCenters x centers of the header digits 1–7; @param dayBaselines baselines of Es…Re. */
    Grid(float[] colCenters, float[] dayBaselines) {
        firstColCenter = colCenters[0];
        colWidth = (colCenters[PERIODS - 1] - colCenters[0]) / (PERIODS - 1);
        rowHeight = (dayBaselines[DAYS - 1] - dayBaselines[0]) / (DAYS - 1);
        firstRowTop = dayBaselines[0] - DAY_LABEL_BASELINE * rowHeight;
    }

    float colLeft(int period) {
        return firstColCenter + (period - 1.5f) * colWidth;
    }

    float colRight(int period) {
        return colLeft(period) + colWidth;
    }

    /** Header, footer and day labels all fall outside. */
    boolean contains(PdfWord w) {
        return w.y() >= firstRowTop && w.y() < firstRowTop + DAYS * rowHeight
                && w.x0() >= colLeft(1) - EDGE_TOLERANCE && w.x1() <= colRight(PERIODS) + EDGE_TOLERANCE;
    }

    /** Weekday 1–5 of a word inside the grid. */
    int day(PdfWord w) {
        return Math.clamp((int) Math.floor((w.y() - firstRowTop) / rowHeight) + 1, 1, DAYS);
    }
}
