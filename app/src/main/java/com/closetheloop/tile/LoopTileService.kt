package com.closetheloop.tile

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.closetheloop.MainActivity
import com.closetheloop.R
import com.closetheloop.appContainer
import com.closetheloop.domain.LoopSummaries
import com.closetheloop.domain.LoopSummary
import com.closetheloop.ui.LoopColors
import com.closetheloop.ui.TimeFormat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first

/** Swipe-left glance: most urgent loop's ring, title, time left, and a Mic button straight into voice capture. */
class LoopTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val now = System.currentTimeMillis()
            val loops = appContainer.repository.observeOpen().first()
            val summary = LoopSummaries.mostUrgent(loops, now)
            val emptyText = if (loops.isEmpty()) "No open loops" else "Nothing ticking"

            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(60_000)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(this@LoopTileService, summary, emptyText)))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = SuspendToFutureAdapter.launchFuture {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                ID_MIC,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(R.drawable.ic_mic).build()
                    )
                    .build(),
            )
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val ID_MIC = "mic"
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DIM = 0xFFB0B0B0.toInt()
        private const val BLACK = 0xFF000000.toInt()

        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(LoopTileService::class.java)
        }

        private fun layout(context: Context, summary: LoopSummary?, emptyText: String): LayoutElement {
            val root = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(launch(context, "open", voice = false)).build())

            val center = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

            if (summary == null) {
                center.addContent(text(emptyText, 15f, DIM, bold = false, maxLines = 1))
            } else {
                val color = LoopColors.display(summary.loop.id, summary.urgency).toArgb()
                // Only a loop still counting down gets a ring, matching the dashboard.
                if (!summary.expired) {
                    root.addContent(arc(360f, color and 0x33FFFFFF))
                    root.addContent(arc(360f * summary.fraction, color))
                }
                center.addContent(text(summary.loop.title, 15f, WHITE, bold = false, maxLines = 2))
                center.addContent(
                    text(
                        if (summary.expired) "Time's up" else TimeFormat.remaining(summary.remainingMs),
                        if (summary.expired) 20f else 28f,
                        if (summary.expired) LoopColors.Red.toArgb() else color,
                        bold = true,
                        maxLines = 1,
                    )
                )
            }
            center.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            center.addContent(micButton(context))
            return root.addContent(center.build()).build()
        }

        private fun arc(sweep: Float, color: Int): LayoutElement =
            LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(degrees(0f))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(
                    LayoutElementBuilders.ArcLine.Builder()
                        .setLength(degrees(sweep))
                        .setThickness(dp(8f))
                        .setColor(argb(color))
                        .build()
                )
                .build()

        private fun text(value: String, size: Float, color: Int, bold: Boolean, maxLines: Int): LayoutElement =
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(150f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(value)
                        .setMaxLines(maxLines)
                        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(size))
                                .setColor(argb(color))
                                .setWeight(
                                    if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL
                                )
                                .build()
                        )
                        .build()
                )
                .build()

        private fun micButton(context: Context): LayoutElement =
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(52f))
                .setHeight(dp(52f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(launch(context, "mic", voice = true))
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(LoopColors.Green.toArgb()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(26f)).build())
                                .build()
                        )
                        .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription("Add loop").build())
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(ID_MIC)
                        .setWidth(dp(26f))
                        .setHeight(dp(26f))
                        .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(BLACK)).build())
                        .build()
                )
                .build()

        private fun launch(context: Context, id: String, voice: Boolean): ModifiersBuilders.Clickable {
            val activity = ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName(MainActivity::class.java.name)
            if (voice) {
                activity.addKeyToExtraMapping(
                    MainActivity.EXTRA_START_VOICE,
                    ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
                )
            }
            return ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
                .build()
        }
    }
}
