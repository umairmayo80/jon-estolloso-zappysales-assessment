import { Avatar } from '@mui/material';

export function initials(firstName: string, lastName: string): string {
  return `${firstName.at(0) ?? ''}${lastName.at(0) ?? ''}`.toUpperCase() || 'PD';
}

export function InitialAvatar({ firstName, lastName, size = 36 }: { firstName: string; lastName: string; size?: number }) {
  return (
    <Avatar
      aria-hidden="true"
      sx={{
        width: size,
        height: size,
        bgcolor: '#DBEAFE',
        color: '#1E3A8A',
        fontSize: size <= 32 ? 11 : 13,
        fontWeight: 700,
      }}
    >
      {initials(firstName, lastName)}
    </Avatar>
  );
}
