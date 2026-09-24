import { Button, Stack, Typography } from "@mui/material";

export function Pagination({
  page,
  total,
  loading,
  onChange,
}: {
  page: number;
  total: number;
  loading: boolean;
  onChange: (page: number) => void;
}) {
  return (
    <Stack
      direction="row"
      sx={{
        gap: 1,
        alignItems: "center",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        mt: 4,
      }}
    >
      <Button
        variant="outlined"
        disabled={loading || !page}
        onClick={() => onChange(page - 1)}
      >
        Previous
      </Button>
      <Typography color="text.secondary" variant="body2" sx={{ px: 1 }}>
        Page {page + 1} of {total}
      </Typography>
      <Button
        variant="outlined"
        disabled={loading || page + 1 >= total}
        onClick={() => onChange(page + 1)}
      >
        Next
      </Button>
    </Stack>
  );
}
