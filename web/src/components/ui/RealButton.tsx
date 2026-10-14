import { cva, type VariantProps } from 'class-variance-authority'
import { forwardRef } from 'react'
import { cn } from '../../lib/cn'

// Ported from karatekool's RealButton: a chunky button with a darker bottom edge that lifts on hover
const realButtonVariants = cva(
  [
    'inline-flex items-center justify-center gap-2',
    'm-0 border-b-[6px] text-center font-semibold tracking-wide whitespace-nowrap',
    'transition-all duration-300 hover:-translate-y-[0.15rem]',
    'active:translate-y-[0.2rem] active:duration-75',
    'focus-visible:outline-[3.5px] focus-visible:outline-offset-2',
    'disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0',
  ],
  {
    variants: {
      variant: {
        sky: 'bg-casual-sky-blue text-white border-casual-sky-blue-darker active:border-casual-sky-blue outline-casual-sky-blue-outline',
        green: 'bg-casual-green text-white border-casual-green-darker active:border-casual-green outline-casual-green-outline',
        orange: 'bg-casual-orange text-white border-casual-orange-darker active:border-casual-orange outline-casual-orange-outline',
        dark: 'bg-gray-700 text-white border-gray-900 active:border-gray-700 outline-gray-400',
        light: 'bg-slate-50 text-slate-700 border-slate-200 hover:text-gray-800 active:border-slate-50 outline-slate-300',
      },
      size: {
        xs: 'py-1.5 px-4 text-sm rounded-xl',
        sm: 'py-2 px-6 text-sm rounded-2xl',
        md: 'py-2.5 px-10 text-base rounded-2xl',
        lg: 'py-3 px-12 text-lg rounded-2xl',
      },
    },
    defaultVariants: { variant: 'sky', size: 'md' },
  },
)

export interface RealButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof realButtonVariants> {}

export const RealButton = forwardRef<HTMLButtonElement, RealButtonProps>(
  ({ className, variant, size, type = 'button', ...props }, ref) => (
    <button type={type} ref={ref} className={cn(realButtonVariants({ variant, size }), className)} {...props} />
  ),
)

RealButton.displayName = 'RealButton'
