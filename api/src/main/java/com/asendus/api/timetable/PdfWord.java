package com.asendus.api.timetable;

/** One drawn text run on a page: x from the left, y is the baseline measured from the top. */
record PdfWord(String text, float x0, float x1, float y, float size) {

    float cx() {
        return (x0 + x1) / 2;
    }
}
