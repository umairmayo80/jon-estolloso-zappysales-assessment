import { useEffect, useRef } from 'react';
import { Alert, AlertTitle, Box, Link } from '@mui/material';
import type { FieldErrors, FieldValues, Path } from 'react-hook-form';

interface FormErrorSummaryProps<T extends FieldValues> {
  errors: FieldErrors<T>;
  submitted: boolean;
}

export function FormErrorSummary<T extends FieldValues>({ errors, submitted }: FormErrorSummaryProps<T>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const entries = Object.entries(errors).filter(([, error]) => Boolean(error?.message));

  useEffect(() => {
    if (submitted && entries.length) containerRef.current?.focus();
  }, [submitted, entries.length]);

  if (!submitted || !entries.length) return null;
  return (
    <Alert ref={containerRef} tabIndex={-1} role="alert" severity="error" sx={{ mb: 3, scrollMarginTop: 88 }}>
      <AlertTitle id="form-error-title">Please review the form</AlertTitle>
      <Box component="ul" sx={{ pl: 2.25, my: 0 }} aria-labelledby="form-error-title">
        {entries.map(([field, error]) => (
          <li key={field}>
            <Link href={`#${field}`} color="inherit" onClick={(event) => {
              event.preventDefault();
              document.getElementById(field)?.focus();
            }}>
              {String(error?.message)}
            </Link>
          </li>
        ))}
      </Box>
    </Alert>
  );
}

export function fieldId<T extends FieldValues>(name: Path<T>): string {
  return name;
}
