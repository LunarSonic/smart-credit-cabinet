import type { SVGProps } from 'react';

/**
 * Официальная иконка Альфа-Банка (логотип «А» с подчёркиванием).
 * SVG-пути взяты из @alfalab/icons-logotype v2.37.3 (MIT).
 */

/** Средний квадратный логотип 24×24 — для навбара */
export function AlfaBankLogoMIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      role="img"
      aria-label="Альфа-Банк"
      focusable="false"
      fill="currentColor"
      width="24"
      height="24"
      viewBox="0 0 24 24"
      {...props}
    >
      <path
        d="M11.983 5.012L10.14 10.49h3.652l-1.74-5.48h-.07zM12.119 2c1.48 0 1.912.883 2.286 2l3.875 11.56h-2.867l-.87-2.748H9.355l-.939 2.748H5.72L9.786 4c.394-1.12.854-2 2.333-2zM5.5 19h13v3h-13v-3z"
        fillRule="evenodd"
        clipRule="evenodd"
      />
    </svg>
  );
}

/** Большой вертикальный логотип 21×32 — для экрана входа */
export function AlfaBankLIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      role="img"
      aria-label="Альфа-Банк"
      focusable="false"
      fill="currentColor"
      width="21"
      height="32"
      viewBox="0 0 21 32"
      {...props}
    >
      <path
        d="M10.471 4.886l-3.081 8.89h6.106l-2.91-8.89h-.115zM10.7 0c2.474 0 3.196 1.432 3.822 3.245L21 22h-4.794l-1.455-4.459H6.077L4.508 22H0L6.798 3.245C7.458 1.426 8.226 0 10.7 0zM0 27h21v5H0v-5z"
        fillRule="evenodd"
        clipRule="evenodd"
      />
    </svg>
  );
}
