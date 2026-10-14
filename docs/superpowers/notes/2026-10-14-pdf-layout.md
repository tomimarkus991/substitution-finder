# PDF layout notes (Task 2 spike)

Measured with PDFBox 3.0.8 `PDFTextStripper` (`setSortByPosition(true)`, `writeString` override) and
`PDFGraphicsStreamEngine` on all pages of the three samples. Coordinates are in PDF points. Text `y` is
`getYDirAdj()` (baseline, measured from the top). Path `y` was converted with `595.32 - y`.

Samples:

| File | Source | Pages | SHA-1 |
|---|---|---|---|
| `opetajad-a.pdf` | `Tunniplaan_õpetajad.pdf` | 46 | 720c36a0… |
| `opetajad-b.pdf` | `Tunniplaan_õpetajad-1.pdf` | 46 | 720c36a0… (**byte-identical to a**) |
| `opetajad-0304.pdf` | `Tunniplaan 03.-04.09 õpetajad.pdf` | 45 | d2da072a… |

`opetajad-0304.pdf` is a two-day timetable (3–4 Sept): every page has lessons only in the Ne and Re rows.
It has no double lessons and no split cells. Its footer says "Tunniplaan loodud:31.08.2026"; a/b say
"03.09.2026".

## 1. Page size and font sizes

Page: A4 landscape, MediaBox = CropBox = `[0, 0, 841.92, 595.32]`, rotation 0, on every page of every file.
Only one font is used (`CIDFont+F1`).

| Text | Size (pt) | Position (file a) |
|---|---|---|
| Title `Õpetaja <name>` | 41 | baseline y = 40.4, centered horizontally |
| School name ("Deutsches Gymnasium Kadriorg, Gonsiori 38, Tallinn") | 12 | x = 11.9, y = 57.0 |
| Column numbers 1–7 | 19 | y = 88.7 (0304: 89.8) |
| Times ("8:45 - 9:30") | 7 | y = 104.6 (0304: 106.2) |
| Day labels Es Te Ko Ne Re | 43 | x ≈ 32–73 |
| Subject / room labels | 10 | row top + 11.9 |
| Class text | 28 | single line: row top + 53.0 |
| Note / group text | 10 | row top + 85.0, centered |
| Footer "Tunniplaan loodud:dd.mm.yyyy" | 12 | x = 11.9–136.9, y = 574.2 |
| Footer "aSc Tunniplaan" | 12 | x = 765.7–830.1, y = 574.2 |

Size counts in file a: 7pt 322, 10pt 1800, 12pt 138, 19pt 322, 28pt 824, 41pt 46, 43pt 230.

## 2. What one `writeString` call contains

One call per drawn label. PDFBox only splits a line where it sees a gap, and the real space glyphs in the
PDF stay inside the call. Examples of single calls: `Õpetaja Bondar Julia`, `10:35 - 11:20`, `Mäe 212`,
`Oru võimla 1`, `21.Kooli ujula`, `Grupp 1/grupp 1/grupp 1`, `3.kl ek õa`.

Subject and room are **separate** calls (e.g. `Ek` x 98.8–108.2 and `217` x 179.9–193.5, same y).

I checked every glyph pair in every call of both PDFs: no call has a gap larger than 1pt between glyphs, so
a call never holds words from two cells. The only anomaly: in `Õpetaja Karamõ kin Deniss` (a p2, 0304 p8)
there is a space glyph at x = 462.2 (width 8.1) followed by `k` at the same x = 462.2. The space has zero
advance, so it's an export artifact. The real name is "Karamõkin". `Illak Pille- Riin` has a real space
(advance 8.6), so keep that one.

## 3. Column and row geometry

Column header x-centers (center of the 19pt digit):

| | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| a/b | 146.15 | 251.40 | 356.55 | 461.75 | 566.90 | 672.15 | 777.25 |
| 0304 | 146.20 | 251.40 | 356.50 | 461.80 | 566.90 | 672.10 | 777.25 |

Column width = 105.18. The vertical grid lines are at x = 93.6, 198.8, 304.1, 409.2, 514.4, 619.6, 724.8,
829.9 in both files, so column centers are exactly halfway between lines.

Day label baselines (43pt):

| | Es | Te | Ko | Ne | Re |
|---|---|---|---|---|---|
| a/b | 165.4 | 256.8 | 348.2 | 439.7 | 531.1 |
| 0304 | 166.9 | 258.0 | 349.1 | 440.2 | 531.2 |

Horizontal grid lines (from the top): a/b 59.5 (header top), 108.3, 199.8, 291.2, 382.7, 474.1, 565.5
(row height 91.45). 0304: 59.5, 110.0, 201.1, 292.2, 383.3, 474.3, 565.5 (row height 91.1, the header is 1.7pt
taller).

The values are identical on every page within a file (46/46 and 45/45 pages). Across files, x is identical
(±0.1) and y shifts by up to 1.5pt. **The day label baseline is not the row center**: it sits 57.1pt below
the row top in a/b (56.9 in 0304), i.e. at `0.625·h`. The row center is 11.4pt above the baseline.

Measured row tops from the labels, `rowTop(d) = baseline(d) − 0.625·h` with `h = (baseline(Re) − baseline(Es)) / 4`:
a: 108.3 (h 91.43), 0304: 110.0 (h 91.08). Both match the drawn lines to 0.1pt.

Vertical positions inside a row (offset from row top, both files within 0.3pt):

| Content | Normal cell | 2 class lines | 3 class lines | Split cell (top half / bottom half) |
|---|---|---|---|---|
| Subject + room | 11.9 | 11.9 | 11.9 | 9.7 / 55.4 |
| Class text | 53.0 | 41.2, 64.9 | 29.4, 53.0, 76.7 | 30.2 / 75.9 |
| Note | 85.0 | 85.0 | 85.0 | 41.6 / 87.3 |

Horizontal positions inside a cell: subject is left-aligned at `cellLeft + 5.2` (single) or `+ 10.4…10.6`
(double). Room is right-aligned at `cellRight − 5.2…5.4` (single) or `− 10.5…10.6` (double). Class text and
notes are centered: `|cx − cellCenter| ≤ 0.3pt`. Wide notes nearly reach the edge (`Grupp 1/grupp 1/grupp 1`
x0 = 311.6 where the subject would be at 309.2), so x0 can't tell notes from subjects; the center can.

## 4. Double lesson (Pupart Krista, a p27, Te 1–2 "5R")

`5R` x = 185.0–212.1, center 198.55. The column 1/2 boundary is 198.8, so the center is off by 0.25pt. The
subject `TÕ` is at x = 104.0–115.5 (column 1 left + 10.4) and the room `017` at x = 279.9–293.4 (column 2
right − 10.7). Both are on the normal label line y = 211.7.

All 12 double lessons in file a: class-text center within 0.3pt of a boundary (Pupart Te 1–2, Te 6–7,
Ko 1–2, Ko 3–4, Ne 6–7, Re 5–6, and others). A single-cell class text is 52pt from any boundary. The path
dump confirms it: on p27 the vertical lines x = 198.8 and x = 724.8 are missing in the Te row.

No triple lessons in the samples: label padding is only ever 5.2 (single) or 10.5 (double), and there are
exactly 12 double-padded subjects, 12 double-padded rooms and 12 boundary-centered class texts.

## 5. Split cell (Ambos Algi, a p9, Ne 5)

Yes: two label lines at different y inside one slot, and a horizontal line between them at y = 428.4 (=
row top + 45.7, half the row), drawn for x 514.4–619.6 only.

```
y=392.3 'Ettevõtlus/digi' (x 519.6–570.6)   '219' (x 600.6–614.2)
y=412.8 '6M'  (28pt, centered)
y=438.0 'Ettevõtlus/digi'                     '217'
y=458.5 '6R'  (28pt, centered)
```

Other split cells (file a): Jürna Ne 5, Loo Es 1, Randmaa Es 6 / Ko 6 / Re 5, Vaarpuu Ko 6. In split cells
a note sits under its own class line (e.g. Loo Es 1: `KKt`/`Võimla 1` y 118.0, `6S` 138.5, note `Tüdrukud`
149.9, then `ujumine`/`21.Kooli ujula` 163.7, `3.1` 184.2). A half can have a subject and no room (Randmaa
Es 6 bottom half, Vaarpuu Ko 6 top half).

## 6. Multi-line class

Yes: one label line, then two or three 28pt lines. Moora Piret (a p6) Ko 1: `Sk`/`319` at 303.1, then
`2.3/2.4/` (320.5), `2.5/2.3V/` (344.2), `2.5V` (367.8), note `sks 1` (376.2). Line pitch 23.6pt. Bondar
(a p1) has the same group without trailing slashes: `2.3/2.4`, `2.5/2.3V`, `2.5V`. So both "line ends with
/" and "line doesn't" occur; `joinClasses` handles both. No class line contains a space.

## 7. Same layout in all three PDFs?

Yes. a and b are byte-identical. 0304 has the same page size, fonts, sizes, column x positions and in-cell
offsets; only the header is 1.7pt taller and rows are 91.1 instead of 91.45 tall. All geometry must be
derived from each page's own header digits and day labels (never hard-coded), as described in question 3.

## 8. "Grupp 1/grupp 1/grupp 1"

One run (one `writeString` call, internal spaces are real glyphs). Joining note words with `" "` is fine;
the expected value in the Task 4 test stays `"Grupp 1/grupp 1/grupp 1"`.

## 9. Decision: text positions only

Double lessons and split cells can be detected reliably from text alone, so the parser doesn't need the
path dump:

- Double lesson: a 28pt class word whose center is within 3pt of an inner column boundary (measured ≤0.3pt,
  single cells are 52pt away). Task 4's `0.15·w` (15.8pt) also works.
- Split cell: more than one label line (subject or room words at different y) inside one slot.

The spec's parsing steps 4–5 should say: "Detected from text positions only (the spike confirmed the class
text of a double lesson is centered on the column boundary within 0.3pt; split cells have two label lines).
Cell borders are not read." (Not edited in this commit.)

## Changes for Task 4

1. **Row geometry is wrong.** Day label baselines are 11.4pt below the row center. Use
   `h = (y(Re) − y(Es)) / 4`, `rowTop(d) = y(day d) − 0.625·h`, `row = floor((y − rowTop(1)) / h) + 1`,
   and `inside` = `rowTop(1) ≤ y < rowTop(1) + 5h` and `colLeft(1) ≤ x0`, `x1 ≤ colRight(7)`.
   With the current nearest-baseline rule and `rowTop = baseline − h/2`:
   - split-cell top labels (row top + 9.7) are nearer to the previous row's baseline, e.g. Ambos
     `Ettevõtlus/digi` y 392.3 → Ko instead of Ne;
   - Monday split-cell labels (y 118.0 < rowTop 119.7) are dropped, e.g. Loo Es 1 `KKt`/`Võimla 1`;
   - normal Monday labels clear the top edge by only 0.5pt (0.3pt in 0304);
   - the footer `aSc Tunniplaan` (x 765.7–830.1, y 574.2, 12pt) falls inside Re/period 7 on **every page**,
     so every teacher would get a note-only lesson on Friday 7th.
   Also ignore words whose size is not 10 or 28 (±1) when filling cells, as a second guard.
2. **Classify small words by horizontal alignment, not by a vertical band or "label row" test.** In the
   (possibly merged) cell: note if `|cx − cellCenter| < 2pt`; otherwise subject if `cx < cellCenter`, else
   room. Measured: notes are within 0.3pt of the center, labels are far off center. The current
   `labelRow` test classifies a note that sits above a second class line as a subject (Loo Es 1
   `Tüdrukud` y 149.9 and Randmaa Es 6 `grupp 1` y 149.9: cx is 0.05pt left of center → "subject"), which
   would create a third bogus lesson. Drop the `\d{3}` room regex: rooms include `Võimla 1`, `Kooli hoov`,
   `aeroobika`, `21.Kooli ujula`, `Mäe 212`, `Oru võimla 1`. Running this rule over both files gives clean
   subject/room/note sets (listed below).
3. **Split a slot into lessons by label lines, not by subject words only.** Group the subject and room
   words by y (±1pt); each label line starts a lesson that takes the class lines and notes with
   `y_i ≤ y < y_{i+1}`. A half can have a subject without a room (Randmaa Es 6, Vaarpuu Ko 6) and a
   note-only cell can have a room without a subject (Reinaru Es 2: room `403`, note `3.kl ek õa`). No label
   line → one lesson with subject "".
4. **Find merged cells first, then classify.** For each row, a 28pt word centered within 3pt of the boundary
   between p and p+1 makes the cell span p..p+1 (left = colLeft(p), right = colRight(p+1), center = the
   boundary). Assign every word of that row whose cx lies in the span to that cell, classify with rule 2
   against the merged cell's center, and emit the same lesson for p and p+1. This replaces the
   `RawLesson` post-merge: with per-column assignment the subject (`TÕ` at colLeft(p) + 10.4) lands only
   in p and the room (`017` at colRight(p+1) − 10.6) only in p+1, and in p the room would be read as a
   subject/note because it is right of p's center but in another column.
5. **Column headers:** accept `[1-7]` only at size 19 (e.g. 15–25), and day labels only at size ≥ 35 (they
   are 43). `CLASS_TEXT_MIN_SIZE = 14` works (class 28, labels 10), but 20 gives more margin from the
   12pt footer and 19pt header.
6. **Title:** one 41pt run starting with `Õpetaja `; `startsWith` works. Drop a space `TextPosition` whose
   next glyph starts at the same x (zero advance) so "Karamõ kin Deniss" becomes "Karamõkin Deniss".
   Keep other spaces ("Illak Pille- Riin" stays as is; the Task 4 test uses that name).
7. **`splitOnGaps`** never splits anything in the samples (no in-run gap > 1pt). It's harmless; keep it as a
   guard or replace it with "one `PdfWord` per `writeString` call".
8. **Tests:** every expected value in Task 4 Step 1 matches the PDF (Bondar first, 46 teachers, Pärnpuu
   Es 1 Ek/221/4R, Es 3 `4R/4M/4S` + `Grupp 1/grupp 1/grupp 1`, Re 4 Aj/6R, Es 5 empty, Moora Ko 1
   `2.3/2.4/2.5/2.3V/2.5V`, Pupart Te 1–2 5R/TÕ/017, Te 3 5D, Ne 7 `5M/5MV` (TÕ/220), Ambos Ne 5 6M/219 +
   6R/217, Malken empty, Reinaru Es 2 note `3.kl ek õa` (room is `403`), Illak Es 1 `Mäe 212`). Since a
   and b are identical, the parameterized test over `opetajad-a.pdf` adds nothing; 0304 is the useful
   second sample. Suggested extra assertions: Loo Ingrid Es 1 → two lessons (`KKt`/`Võimla 1`/`6S`/note
   `Tüdrukud` and `ujumine`/`21.Kooli ujula`/`3.1`), and no teacher has a lesson with note
   `aSc Tunniplaan`.

## Teachers

**opetajad-a.pdf and opetajad-b.pdf (46 each, identical, in page order):**
Bondar Julia, Karamõ kin Deniss, Kesa Külliki, Kirkmann Mari-Ly, Kirs Õie, Moora Piret, Pihla Kaidi,
Utt Merike, Ambos Algi, Arov Helen, Hiiesalu Anu, Hirv Eleriin, Illak Pille- Riin, Jürna Vivika,
Kitsing Mari, Kriit Kadi, Labanova Marika, Liivamägi Arvo, Kiik Raina, Lipstuhl Margit, Loo Ingrid,
Malken Mariliis, Marran Merike, Normak Mai, Pajur Kristi, Pappel Tiia, Pupart Krista, Puutöö,
Põldma Ragnar, Pärnpuu Taimi, Püss Ken, Randmaa Janell, Reimann Kaidi- Kätlyn, Reinaru Tiia,
Rentel Aivi, Rummi Minna, Saaret Annika, Selge Piia, Zorina Oksana, Tints Birgitta, Tuisk Katre,
Undusk Marlen Elisabeth, Vaarpuu Annika, Vaher Elis, Ingise keel Inglise keel, Teesalu Lea.

**opetajad-0304.pdf (45, alphabetical-ish):**
Ambos Algi, Arov Helen, Bondar Julia, Hiiesalu Anu, Hirv Eleriin, Illak Pille- Riin, Jürna Vivika,
Karamõ kin Deniss, Kesa Külliki, Kirkmann Mari-Ly, Kirs Õie, Kitsing Mari, Kriit Kadi, Labanova Marika,
Liivamägi Arvo, Kiik Raina, Lipstuhl Margit, Loo Ingrid, Malken Mariliis, Marran Merike, Moora Piret,
Normak Mai, Pajur Kristi, Pappel Tiia, Pihla Kaidi, Pupart Krista, Puutöö, Põldma Ragnar, Pärnpuu Taimi,
Püss Ken, Randmaa Janell, Reimann Kaidi- Kätlyn, Reinaru Tiia, Rentel Aivi, Rummi Minna, Saaret Annika,
Selge Piia, Zorina Oksana, Tints Birgitta, Tuisk Katre, Undusk Marlen Elisabeth, Utt Merike,
Vaarpuu Annika, Vaher Elis, Ingise keel Inglise keel.

Difference: Teesalu Lea is only in a/b. Placeholders: Puutöö, Ingise keel Inglise keel. Malken Mariliis has
an empty grid in a.

## Subject codes, rooms, notes (both files, by rule 2 above)

Subjects (23): Aj, Digikirjaoskus, E2, Ek, Ettevõtlus/digi, Ettevõtlusõpe, Ink, InÕ, KKp, KKt, Kirjandus,
Kk, Ku, Kunst/digi, Lo, Ma, Mu, Sk, Tehnol/digi, TÕ, matem/digi, ujumine, Üh.

Rooms: 016, 017, 108, 121, 124, 128, 130, 131, 132, 133, 134, 204, 206, 207, 211, 213, 216, 217, 218, 219,
220, 221, 304, 309, 311, 314–320, 403, 414, 21.Kooli ujula, Kooli hoov, Mäe 212, Mäe võimla, Oru Võimla 2,
Oru võimla, Oru võimla 1, Võimla 1, Võimla 2, aeroobika (an activity, but drawn in the room slot).

Notes: Grupp 1/2, grupp 1/2/3, Grupp 1/grupp 1/grupp 1, grupp 3/grupp 3/Grupp 3, Poisid, Tüdrukud,
Sk1–3, Sks 1–3, sks 1–4, sks1, sks2, sks 1/Sk 1, sks 1/Sks 1 (and 2, 3), sks 3/Sks3, 3.kl ek õa.
