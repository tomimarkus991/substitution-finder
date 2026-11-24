/**
 * A date range as one button that opens a month calendar (from reisid24, modelled on gatherr's
 * create-event calendar): a season emoji and the month in the header, months that slide left or right,
 * and chosen days marked with a primary circle that springs in.
 *
 * - The first day pressed starts the range, the second ends it (an earlier second day swaps them);
 *   the range is applied, and the calendar closes, on the second press. Pressing the same day again
 *   chooses just that day.
 * - Pressing the month name chooses that whole month at once (from `min` if the month starts before it).
 * - `clearLabel` adds a small text link that empties the range ("Any dates", "Kogu aeg").
 * - `min` (ISO day) disables earlier days, e.g. today for departures. Leave it out for history.
 * - Arrow keys move between days, Page Up/Down between months, Escape or a click outside closes.
 * - Dates are ISO days (`2026-11-18`) in the viewer's local calendar. No date library needed.
 *
 * Usage:
 *   const [range, setRange] = useState<DateRange>({ from: null, to: null });
 *   <DateRangePicker value={range} onChange={setRange} min={todayIso} />
 *   <DateRangePicker value={range} onChange={setRange} locale="et-EE" labels={ET_LABELS} clearLabel="Kogu aeg" />
 */
import { AnimatePresence, motion } from "motion/react";
import { type KeyboardEvent, useEffect, useId, useRef, useState } from "react";

export interface DateRange {
  from: string | null;
  to: string | null;
}

export interface DateRangeLabels {
  /** Accessible name of the button and dialog. */
  name: string;
  any: string;
  only: (day: string) => string;
  between: (from: string, to: string) => string;
  since: (day: string) => string;
  until: (day: string) => string;
  previousMonth: string;
  nextMonth: string;
  wholeMonth: (month: string) => string;
  pickFirst: string;
  pickSecond: string;
}

export const EN_LABELS: DateRangeLabels = {
  name: "Dates",
  any: "Any dates",
  only: d => `Only ${d}`,
  between: (a, b) => `${a} – ${b}`,
  since: d => `From ${d}`,
  until: d => `Until ${d}`,
  previousMonth: "Previous month",
  nextMonth: "Next month",
  wholeMonth: m => `Choose all of ${m}`,
  pickFirst: "Choose the first day, or press the month name for all of it.",
  pickSecond: "Now choose the last day, or the same day again for just that day.",
};

interface Props {
  value: DateRange;
  onChange: (range: DateRange) => void;
  /** Earliest day that can be chosen (ISO). */
  min?: string;
  /** BCP 47 locale for month and weekday names. @default "en-GB" */
  locale?: string;
  labels?: DateRangeLabels;
  /** Shows a text link that clears the range. */
  clearLabel?: string;
  className?: string;
}

const SEASON = ["⛄️", "⛄️", "🌸", "🌸", "🌸", "🌻", "🌻", "🌻", "🍂", "🍂", "🍂", "⛄️"];

const pad = (n: number) => String(n).padStart(2, "0");
const toIso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const toDate = (iso: string) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
};
const addDays = (iso: string, n: number) => {
  const d = toDate(iso);
  d.setDate(d.getDate() + n);
  return toIso(d);
};
const firstOfMonth = (iso: string) => `${iso.slice(0, 7)}-01`;
const addMonths = (month: string, n: number) => {
  const d = toDate(month);
  return toIso(new Date(d.getFullYear(), d.getMonth() + n, 1));
};
const lastOfMonth = (month: string) => {
  const d = toDate(month);
  return toIso(new Date(d.getFullYear(), d.getMonth() + 1, 0));
};
const mondayIndex = (iso: string) => (toDate(iso).getDay() + 6) % 7;

/** The month's days in whole weeks from Monday, including the neighbours' days at the edges. */
const weeksOf = (month: string) => {
  const start = addDays(month, -mondayIndex(month));
  const last = lastOfMonth(month);
  const end = addDays(last, 6 - mondayIndex(last));
  const days: string[] = [];
  for (let d = start; d <= end; d = addDays(d, 1)) days.push(d);
  return days;
};

const slide = {
  enter: (direction: number) => ({ x: `${direction * 100}%`, opacity: 0 }),
  middle: { x: 0, opacity: 1 },
  exit: (direction: number) => ({ x: `${direction * -100}%`, opacity: 0 }),
};

export const DateRangePicker = ({
  value: { from, to },
  onChange,
  min,
  locale = "en-GB",
  labels = EN_LABELS,
  clearLabel,
  className,
}: Props) => {
  const [today] = useState(() => toIso(new Date()));
  const [open, setOpen] = useState(false);
  /** The first day pressed, while the second is still to come. */
  const [start, setStart] = useState<string | null>(null);
  const [month, setMonth] = useState(() => firstOfMonth(from ?? today));
  const [direction, setDirection] = useState(1);
  const [focused, setFocused] = useState(from ?? today);
  const root = useRef<HTMLDivElement>(null);
  const grid = useRef<HTMLDivElement>(null);
  const panelId = useId();

  const fmtShort = new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" });
  const fmtMonth = new Intl.DateTimeFormat(locale, { month: "long", year: "numeric" });
  const fmtDay = new Intl.DateTimeFormat(locale, { weekday: "short", day: "numeric", month: "long", year: "numeric" });
  const fmtWeekday = new Intl.DateTimeFormat(locale, { weekday: "narrow" });
  const short = (iso: string) => fmtShort.format(toDate(iso));

  const rangeText = () => {
    if (from && from === to) return labels.only(short(from));
    if (from && to) return labels.between(short(from), short(to));
    if (from) return labels.since(short(from));
    if (to) return labels.until(short(to));
    return labels.any;
  };

  const close = () => {
    setOpen(false);
    setStart(null);
  };

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) close();
    };
    document.addEventListener("pointerdown", onPointer);
    return () => document.removeEventListener("pointerdown", onPointer);
  }, [open]);

  // Keyboard focus follows the focused day, also into a newly shown month.
  useEffect(() => {
    if (!open) return;
    grid.current?.querySelector<HTMLButtonElement>(`[data-day="${focused}"]`)?.focus({ preventScroll: true });
  }, [open, focused, month]);

  const toggle = () => {
    if (open) return close();
    const first = from ?? today;
    setMonth(firstOfMonth(first));
    setFocused(first);
    setOpen(true);
  };

  const showMonth = (step: number) => {
    setDirection(step);
    setMonth(current => addMonths(current, step));
  };

  const moveFocus = (day: string) => {
    if (day < month) setDirection(-1);
    if (day > lastOfMonth(month)) setDirection(1);
    setMonth(firstOfMonth(day));
    setFocused(day);
  };

  const disabled = (day: string) => min !== undefined && day < min;

  const choose = (day: string) => {
    if (disabled(day)) return;
    if (start === null) {
      setStart(day);
      setFocused(day);
      return;
    }
    const [first, last] = day < start ? [day, start] : [start, day];
    onChange({ from: first, to: last });
    close();
  };

  const chooseMonth = () => {
    const last = lastOfMonth(month);
    if (disabled(last)) return;
    onChange({ from: min && min > month ? min : month, to: last });
    close();
  };

  const clear = () => {
    onChange({ from: null, to: null });
    close();
  };

  const onKeyDown = (event: KeyboardEvent) => {
    const steps: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
    if (event.key === "Escape") {
      event.preventDefault();
      close();
      root.current?.querySelector<HTMLButtonElement>("button")?.focus();
    } else if (event.key in steps && (event.target as HTMLElement).dataset.day) {
      event.preventDefault();
      moveFocus(addDays(focused, steps[event.key]));
    } else if (event.key === "PageUp" || event.key === "PageDown") {
      event.preventDefault();
      const d = toDate(focused);
      const target = new Date(d.getFullYear(), d.getMonth() + (event.key === "PageUp" ? -1 : 1), 1);
      moveFocus(toIso(target));
    }
  };

  // The range to draw: the one being chosen (start only), or the chosen one.
  const [shownFrom, shownTo] = start !== null ? [start, null] : [from, to];
  const days = weeksOf(month);
  const weekdays = days.slice(0, 7).map(day => fmtWeekday.format(toDate(day)));
  const monthName = fmtMonth.format(toDate(month));
  const wholeMonthChosen = from === (min && min > month ? min : month) && to === lastOfMonth(month);

  return (
    <div ref={root} className={`relative ${className ?? ""}`}>
      <button
        type="button"
        aria-label={labels.name}
        aria-expanded={open}
        aria-controls={panelId}
        aria-description={rangeText()}
        onClick={toggle}
        className="flex w-full items-center justify-between gap-3 rounded-xl border border-border bg-surface px-3 py-2 text-left text-text transition-colors hover:border-primary focus-visible:border-primary"
      >
        <span className="font-number font-semibold">{rangeText()}</span>
        <span aria-hidden>📅</span>
      </button>
      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label={labels.name}
          onKeyDown={onKeyDown}
          className="absolute left-0 z-40 mt-2 w-72 overflow-hidden rounded-xl bg-surface p-3 shadow-lg ring-1 ring-border"
        >
          <header className="relative mb-2 flex h-8 items-center justify-between">
            <button
              type="button"
              aria-label={labels.previousMonth}
              onClick={() => showMonth(-1)}
              className="z-10 rounded-full px-2 text-xl leading-none text-text transition-transform hover:scale-110 active:scale-90"
            >
              ‹
            </button>
            <AnimatePresence initial={false} mode="popLayout" custom={direction}>
              <motion.div
                key={month}
                custom={direction}
                variants={slide}
                initial="enter"
                animate="middle"
                exit="exit"
                transition={{ duration: 0.25, ease: "easeOut" }}
                className="absolute inset-x-8 inset-y-0 flex items-center justify-center"
              >
                {/* Pressing the month name chooses the whole month */}
                <button
                  type="button"
                  onClick={chooseMonth}
                  aria-label={labels.wholeMonth(monthName)}
                  aria-live="polite"
                  className={`rounded-full px-3 py-1 font-semibold transition-colors hover:bg-primary/15 active:scale-95 ${
                    wholeMonthChosen ? "bg-primary text-white hover:bg-primary" : "text-text"
                  }`}
                >
                  <span className="mr-2" aria-hidden>
                    {SEASON[toDate(month).getMonth()]}
                  </span>
                  {monthName}
                </button>
              </motion.div>
            </AnimatePresence>
            <button
              type="button"
              aria-label={labels.nextMonth}
              onClick={() => showMonth(1)}
              className="z-10 rounded-full px-2 text-xl leading-none text-text transition-transform hover:scale-110 active:scale-90"
            >
              ›
            </button>
          </header>
          <div className="mb-1 grid grid-cols-7 text-center text-xs font-semibold text-text-muted" aria-hidden>
            {weekdays.map((day, index) => (
              <span key={index}>{day}</span>
            ))}
          </div>
          <AnimatePresence initial={false} mode="popLayout" custom={direction}>
            <motion.div
              key={month}
              ref={grid}
              custom={direction}
              variants={slide}
              initial="enter"
              animate="middle"
              exit="exit"
              transition={{ duration: 0.25, ease: "easeOut" }}
              role="grid"
              aria-label={monthName}
              className="grid grid-cols-7 gap-y-0.5"
            >
              {days.map(day => {
                const inMonth = day.slice(0, 7) === month.slice(0, 7);
                const off = disabled(day);
                const isEnd = day === shownFrom || day === shownTo;
                const between = shownFrom !== null && shownTo !== null && day > shownFrom && day < shownTo;
                return (
                  <button
                    key={day}
                    type="button"
                    role="gridcell"
                    data-day={day}
                    aria-label={fmtDay.format(toDate(day))}
                    aria-selected={isEnd || between}
                    aria-disabled={off}
                    tabIndex={day === focused ? 0 : -1}
                    onClick={() => choose(day)}
                    className={`relative h-9 rounded-lg text-sm outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                      between ? "bg-primary/15" : ""
                    } ${off ? "cursor-not-allowed opacity-30" : "cursor-pointer"} ${
                      day === today && !isEnd ? "font-bold text-primary" : inMonth ? "text-text" : "text-text-muted"
                    }`}
                  >
                    <motion.span
                      aria-hidden
                      initial={false}
                      animate={{ scale: isEnd ? 1 : 0, opacity: isEnd ? 1 : 0 }}
                      transition={{ type: "spring", stiffness: 300, damping: 30 }}
                      className="absolute inset-0.5 rounded-full bg-primary"
                    />
                    <span className={`relative font-number font-medium ${isEnd ? "text-white" : ""}`}>
                      {toDate(day).getDate()}
                    </span>
                  </button>
                );
              })}
            </motion.div>
          </AnimatePresence>
          <div className="mt-2 flex items-end justify-between gap-3">
            <p className="text-xs text-text-muted" aria-live="polite">
              {start === null ? labels.pickFirst : labels.pickSecond}
            </p>
            {clearLabel && (from || to) && (
              <button
                type="button"
                onClick={clear}
                className="shrink-0 text-xs font-semibold text-primary underline-offset-2 hover:underline"
              >
                {clearLabel}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
