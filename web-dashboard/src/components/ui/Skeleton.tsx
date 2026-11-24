import React from 'react';

export interface SkeletonProps {
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className = '' }) => {
  return (
    <div
      className={`animate-pulse bg-slate-200 dark:bg-slate-800 rounded-lg ${className}`}
      aria-hidden="true"
    />
  );
};

export const CardSkeleton: React.FC = () => {
  return (
    <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] space-y-4">
      <div className="flex items-start justify-between">
        <div className="space-y-2">
          <Skeleton className="w-32 h-5" />
          <Skeleton className="w-24 h-3.5" />
        </div>
        <Skeleton className="w-16 h-6 rounded-full" />
      </div>
      <div className="space-y-2 pt-2 border-t border-slate-100 dark:border-slate-800">
        <Skeleton className="w-full h-4" />
        <Skeleton className="w-3/4 h-4" />
        <Skeleton className="w-1/2 h-4" />
      </div>
    </div>
  );
};
