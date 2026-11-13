package com.chantingscheduler.app.ui

import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chantingscheduler.app.R
import com.chantingscheduler.app.data.MotionStore
import com.chantingscheduler.app.ui.motion.ArtScene
import com.chantingscheduler.app.ui.motion.LocalMotion
import com.chantingscheduler.app.ui.motion.MotionLevel
import com.chantingscheduler.app.ui.motion.MotionPolicy
import com.chantingscheduler.app.ui.motion.MotionTokens
import com.chantingscheduler.app.ui.motion.SceneArt
import com.chantingscheduler.app.ui.motion.artSceneContentDescription
import com.chantingscheduler.app.ui.motion.rememberAmbientActive
import com.chantingscheduler.app.ui.motion.sceneBackdropBrush

/**
 * Settings → "Motion & scenery": the motion level (Calm / Minimal / Off) and
 * the scene (Monk / Lotus / Temple dawn / Abstract light), each with live
 * previews animated at the currently effective level. Reads [MotionStore]
 * directly; the effective level still caps at the system animator scale, and
 * when the system forces Off a helper line says so.
 */
@Composable
fun MotionScenerySection() {
    val context = LocalContext.current
    val store = remember { MotionStore.get(context) }
    val storedLevel by store.motionLevel.collectAsStateWithLifecycle()
    val storedScene by store.scene.collectAsStateWithLifecycle()
    val level = MotionPolicy.levelFromStored(storedLevel)
    val scene = ArtScene.fromStored(storedScene)
    val systemScale = rememberAnimatorDurationScale()
    val forcedOff = systemScale == 0f

    SectionOverline(stringResource(R.string.motion_section))

    Text(
        stringResource(R.string.motion_level_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MotionLevel.entries.forEach { option ->
            LevelCard(
                option = option,
                selected = level == option,
                onClick = { store.setMotionLevel(MotionPolicy.levelToStored(option)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (forcedOff) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.motion_system_forced_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(18.dp))
    Text(
        stringResource(R.string.scene_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtScene.entries.forEach { option ->
            SceneCard(
                scene = option,
                selected = scene == option,
                animate = level == MotionLevel.CALM && rememberAmbientActive(),
                onClick = { store.setScene(option.storedName) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LevelCard(
    option: MotionLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(
        when (option) {
            MotionLevel.CALM -> R.string.motion_calm
            MotionLevel.MINIMAL -> R.string.motion_minimal
            MotionLevel.OFF -> R.string.motion_off
        },
    )
    val body = stringResource(
        when (option) {
            MotionLevel.CALM -> R.string.motion_calm_body
            MotionLevel.MINIMAL -> R.string.motion_minimal_body
            MotionLevel.OFF -> R.string.motion_off_body
        },
    )
    val selectedState = stringResource(
        if (selected) R.string.state_selected else R.string.state_not_selected,
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
            stateDescription = selectedState
        },
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SceneCard(
    scene: ArtScene,
    selected: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(
        when (scene) {
            ArtScene.MONK -> R.string.scene_monk
            ArtScene.LOTUS -> R.string.scene_lotus
            ArtScene.TEMPLE -> R.string.scene_temple
            ArtScene.ABSTRACT -> R.string.scene_abstract
        },
    )
    val description = artSceneContentDescription(scene)
    val selectedState = stringResource(
        if (selected) R.string.state_selected else R.string.state_not_selected,
    )
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = MotionTokens.springSlide(),
        label = "sceneCardRing",
    )
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$name — $description"
                stateDescription = selectedState
            }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(sceneBackdropBrush(com.chantingscheduler.app.ui.motion.DayPhase.DAY))
                .then(
                    if (selected) {
                        Modifier.border(ringWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            SceneArt(
                scene = scene,
                phase = com.chantingscheduler.app.ui.motion.DayPhase.DAY,
                animate = animate,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** System animator duration scale; 0 means "remove animations" is active. */
@Composable
private fun rememberAnimatorDurationScale(): Float {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
}
