import { useCallback, useEffect, useRef } from 'react';
import { type BlockerFunction, useBlocker } from 'react-router-dom';

interface UnsavedChangesGuard {
  isBlocked: boolean;
  discardChanges: () => void;
  keepEditing: () => void;
  navigateWithoutPrompt: (navigate: () => void | Promise<void>) => void;
}

export function useUnsavedChanges(isDirty: boolean): UnsavedChangesGuard {
  const skipNextNavigation = useRef(false);
  const shouldBlock = useCallback<BlockerFunction>(({ currentLocation, nextLocation }) => {
    if (!isDirty || skipNextNavigation.current) return false;
    return currentLocation.pathname !== nextLocation.pathname
      || currentLocation.search !== nextLocation.search
      || currentLocation.hash !== nextLocation.hash;
  }, [isDirty]);
  const blocker = useBlocker(shouldBlock);

  useEffect(() => {
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    if (isDirty) window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [isDirty]);

  const discardChanges = useCallback(() => {
    if (blocker.state === 'blocked') blocker.proceed();
  }, [blocker]);

  const keepEditing = useCallback(() => {
    if (blocker.state === 'blocked') blocker.reset();
  }, [blocker]);

  const navigateWithoutPrompt = useCallback((navigate: () => void | Promise<void>) => {
    // React Router evaluates blockers synchronously when navigate() is called.
    // Limit this escape hatch to the successful save redirect that invoked it.
    skipNextNavigation.current = true;
    try {
      void navigate();
    } finally {
      skipNextNavigation.current = false;
    }
  }, []);

  return {
    isBlocked: blocker.state === 'blocked',
    discardChanges,
    keepEditing,
    navigateWithoutPrompt,
  };
}
