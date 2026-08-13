import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.offlinetranslator.App
import dev.nucleusframework.offlinetranslator.main.BrandLabel
import dev.nucleusframework.offlinetranslator.main.LocalHostHasTitleBar
import dev.nucleusframework.offlinetranslator.main.LocalSystemMeters
import dev.nucleusframework.offlinetranslator.main.LocalWindowDrag
import dev.nucleusframework.offlinetranslator.main.SystemMeters
import dev.nucleusframework.offlinetranslator.theme.rememberEdgeColorScheme
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.LocalWindowChromeInsets
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.windowDragArea
import io.github.vinceglb.filekit.FileKit

private const val DESKTOP_DENSITY_SCALE = 0.75f
private val CHROME_HEIGHT = 48.dp

fun main(args: Array<String>) {
    FileKit.init(appId = "EdgeTranslator")
    val forceOnboarding = "--onboarding" in args
    nucleusApplication(args) {
        // The chrome sits outside App's own EdgeTheme, so the app's theme setting
        // has to come back out here — otherwise the title bar keeps following the
        // OS while the content goes light/dark. Seeded from the OS: App overrides
        // it as soon as the store has loaded.
        val systemDark = isSystemInDarkTheme()
        var dark by remember { mutableStateOf(systemDark) }
        // Same story for the interface language: an RTL one has to flip the chrome too, and the
        // window controls sit outside App's own LocalLayoutDirection.
        var rtl by remember { mutableStateOf(false) }
        val colors = rememberEdgeColorScheme(dark)

        MaterialTheme(colorScheme = colors) {
            MaterialDecoratedWindow(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 1440.dp, height = 900.dp),
                title = "Edge Translator",
                minimumSize = DpSize(960.dp, 640.dp),
            ) {
                val windowScope = this
                // The colour behind everything Compose does not paint (live
                // resize frames, the margin around a panel) and the appearance
                // of the native surfaces — traffic lights, glass, popups.
                WindowBackground(colors.background)
                WindowAppearance(if (dark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)

                // Custom chrome in the scaffold's title-bar slot: the scaffold
                // measures it and publishes the height to the native layer
                // (macOS traffic-light centering, Windows caption zone).
                //
                // The title bar is a sibling of the content, so App's own LocalLayoutDirection
                // never reaches it — an RTL interface language has to be handed over separately.
                // The scaffold flips the AppKit traffic-lights natively off this same value.
                val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                WindowScaffold(
                    modifier = Modifier.macOSLargeCornerRadius(),
                    controlButtonsDirection = if (rtl) ControlButtonsDirection.Rtl else ControlButtonsDirection.Ltr,
                    titleBar = {
                        CompositionLocalProvider(LocalLayoutDirection provides dir) { windowScope.AppChrome() }
                    },
                ) {
                    // Lock the app content to 75% density (denser UI). Scaled off the monitor's
                    // own density so it stays a true 75% on both retina and standard DPI.
                    // The chrome stays at native density so it keeps crisp next to the
                    // window controls.
                    val base = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(base.density * DESKTOP_DENSITY_SCALE, base.fontScale),
                        LocalHostHasTitleBar provides true,
                        // Lets the shared UI declare its own drag surfaces — the
                        // navigation rail — without depending on Nucleus.
                        LocalWindowDrag provides Modifier.windowDragArea(),
                        // Host-only: the meters read this machine via system-info.
                        LocalSystemMeters provides { modifier -> SystemMeters(modifier) },
                    ) {
                        App(
                            // SideEffect: App reports the resolved theme during
                            // composition, so the write has to land after it.
                            onThemeChanged = { isDark -> SideEffect { dark = isDark } },
                            onLayoutDirectionChanged = { isRtl -> SideEffect { rtl = isRtl } },
                            onQuit = ::exitApplication,
                            forceOnboarding = forceOnboarding,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Window chrome: the app title, and nothing else. Navigation, the meters and
 * each screen's own strip live in the app body.
 *
 * The whole strip is the drag surface — `WindowScaffold` makes nothing implicit.
 */
@Composable
private fun DecoratedWindowScope.AppChrome() {
    val colors = MaterialTheme.colorScheme
    val insets = LocalWindowChromeInsets.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(CHROME_HEIGHT)
            .background(colors.surfaceContainer)
            .windowDragArea(),
    ) {
        // controlsInsets keeps the title clear of the zones the platform owns:
        // the macOS traffic-lights floating over the leading edge, the KDE edge
        // padding. Reserved here *or* by placing WindowControls, never both, or
        // the gap is counted twice.
        //
        // Read as absolute values: the scaffold already mirrored this reserve off
        // controlButtonsDirection, so resolving it a second time against an RTL layout would send
        // it back to the side the traffic-lights just left, straight under the title.
        // Alignment and the 12.dp gap stay relative — those *should* follow the language.
        val reserve = insets.controlsInsets
        BrandLabel(
            Modifier
                .align(Alignment.CenterStart)
                .absolutePadding(
                    left = reserve.calculateLeftPadding(LayoutDirection.Ltr),
                    right = reserve.calculateRightPadding(LayoutDirection.Ltr),
                )
                .padding(start = 12.dp),
        )
        // macOS draws real traffic-lights; every other platform is fully
        // undecorated, so the window would have no controls at all.
        if (Platform.Current != Platform.MacOS) {
            WindowControls(Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
