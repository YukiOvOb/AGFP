import type { ReactNode } from 'react';
type Props = {
  /** Short uppercase line above the title, for example "SEARCH AND RESERVE". */
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  /** Buttons shown on the right, for example the page's primary action. */
  actions?: ReactNode;
};
/** The one `h1` of a page, with the same spacing and type on every page. */
export function PageHeader({ eyebrow, title, description, actions }: Props) {
  return (
    <div className="page-title">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>
          {title}
          <span className="title-dot" aria-hidden="true">
            .
          </span>
        </h1>
        {description && <p className="muted">{description}</p>}
      </div>
      {actions}
    </div>
  );
}
