"use client";

import { AppRouterCacheProvider } from "@mui/material-nextjs/v16-appRouter";
import { CssBaseline } from "@mui/material";
import { ThemeProvider, createTheme } from "@mui/material/styles";

export const theme = createTheme({
  cssVariables: { colorSchemeSelector: "class" },
  colorSchemes: {
    light: { palette: { primary: { main: "#245941", contrastText: "#ffffff" }, secondary: { main: "#d8ef83", contrastText: "#183729" }, background: { default: "#f4f6f0", paper: "#fffefa" }, text: { primary: "#172b25", secondary: "#52675d" }, divider: "#d7e2d6", error: { main: "#a6413e" } } },
    dark: { palette: { primary: { main: "#b6db96", contrastText: "#14281c" }, secondary: { main: "#d6ed8b", contrastText: "#14281c" }, background: { default: "#101f1a", paper: "#1c3027" }, text: { primary: "#eff7ed", secondary: "#b3c9b9" }, divider: "#3d5948", error: { main: "#ffb5a9" } } },
  },
  typography: { fontFamily: "var(--font-poppins), sans-serif", h1: { fontWeight: 700, letterSpacing: "-.055em" }, h2: { fontWeight: 650, letterSpacing: "-.04em" }, h5: { fontWeight: 650, letterSpacing: "-.035em" }, button: { textTransform: "none", fontWeight: 650 } },
  shape: { borderRadius: 16 },
  components: {
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { borderRadius: 12, minHeight: 40, paddingInline: 18 } } },
    MuiCard: { styleOverrides: { root: { borderRadius: 24, border: "1px solid var(--mui-palette-divider)", boxShadow: "0 12px 36px rgba(15,48,30,.07)" } } },
    MuiTextField: { defaultProps: { size: "small", variant: "outlined" } },
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: 12 } } },
    MuiDialog: { styleOverrides: { paper: { borderRadius: 28, backgroundImage: "none" } } },
  },
});

export default function Providers({ children }: { children: React.ReactNode }) {
  return <AppRouterCacheProvider><ThemeProvider theme={theme} defaultMode="system" modeStorageKey="paperless_theme" disableTransitionOnChange><CssBaseline />{children}</ThemeProvider></AppRouterCacheProvider>;
}
