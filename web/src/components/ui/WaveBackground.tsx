import { cn } from '../../lib/cn'

// karatekool's layered waves, recoloured blue in public/waves.svg
export function WaveBackground({ className }: { className?: string }) {
  return (
    <img
      src="/waves.svg"
      alt=""
      aria-hidden
      className={cn('pointer-events-none block h-32 w-full select-none sm:h-44', className)}
    />
  )
}
