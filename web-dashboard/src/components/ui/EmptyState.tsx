import React, { ReactNode } from 'react';
import { LucideIcon } from 'lucide-react';

export interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: {
    label: string;
    onClick: () => void;
    icon?: LucideIcon;
  };
  children?: ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon: Icon,
  title,
  description,
  action,
  children,
}) => {
  return (
    <div className="p-10 sm:p-14 text-center rounded-2xl border border-slate-200 dark:border-[#223049] bg-white dark:bg-[#121927] shadow-sm flex flex-col items-center justify-center space-y-3">
      <div className="w-12 h-12 rounded-2xl bg-slate-100 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-slate-400 dark:text-slate-400 mb-1">
        <Icon className="w-6 h-6 opacity-75" />
      </div>
      <h3 className="text-base font-bold text-slate-900 dark:text-white font-mono">{title}</h3>
      <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm leading-relaxed">
        {description}
      </p>
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="mt-2 inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-sm transition-all"
        >
          {action.icon && <action.icon className="w-3.5 h-3.5" />}
          <span>{action.label}</span>
        </button>
      )}
      {children}
    </div>
  );
};
