import { motion } from 'motion/react'
import { NavLink } from 'react-router'
import { cn } from '../../lib/cn'

interface Item {
  to: string
  label: string
  /** Overrides NavLink's own matching, e.g. "Täna" stays active on any day. */
  active?: boolean
}

/**
 * The design-library SlidingTabs as a nav (karatekool's MovingBox): a white pill slides under the
 * current page. The pill is a shared-layout element, so it handles labels of different widths.
 */
export function SlidingNav({ items, className }: { items: Item[]; className?: string }) {
  return (
    <div className={cn('relative flex rounded-2xl bg-surface-raised/80 p-1 ring-1 ring-border', className)}>
      {items.map(item => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) => {
            const selected = item.active ?? isActive
            return cn(
              'relative flex-1 rounded-xl px-1 py-1.5 text-center text-sm font-bold whitespace-nowrap sm:flex-none sm:px-3 sm:text-[0.95rem]',
              'transition-[color,opacity] duration-500 ease-out focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
              selected ? 'text-primary-dark' : 'text-text opacity-45 hover:opacity-100',
            )
          }}
        >
          {({ isActive }) => (
            <>
              {(item.active ?? isActive) && (
                <motion.span
                  layoutId="nav-pill"
                  aria-hidden
                  className="absolute inset-0 rounded-xl bg-surface shadow-md"
                  transition={{ ease: 'easeOut', duration: 0.4 }}
                />
              )}
              <span className="relative">{item.label}</span>
            </>
          )}
        </NavLink>
      ))}
    </div>
  )
}
