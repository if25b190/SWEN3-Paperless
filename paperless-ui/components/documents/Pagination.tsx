import { Button, Stack, Typography } from "@mui/material";
import { useI18n } from "../../lib/i18n/I18nProvider";

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
  const { t } = useI18n();
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
        {t("common.previous")}
      </Button>
      <Typography color="text.secondary" variant="body2" sx={{ px: 1 }}>
        {t("common.page_of", { page: page + 1, total })}
      </Typography>
      <Button
        variant="outlined"
        disabled={loading || page + 1 >= total}
        onClick={() => onChange(page + 1)}
      >
        {t("common.next")}
      </Button>
    </Stack>
  );
}
