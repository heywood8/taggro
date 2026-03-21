package com.heywood8.telegramnews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.heywood8.telegramnews.domain.model.PhotoLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val showChannelIcons by viewModel.showChannelIcons.collectAsStateWithLifecycle()
    val photoLayout by viewModel.photoLayout.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                "Display",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Show channel icons") },
                supportingContent = { Text("Show a channel icon next to the channel name") },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    Switch(
                        checked = showChannelIcons,
                        onCheckedChange = { viewModel.setShowChannelIcons(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Photo layout") },
                supportingContent = { Text("Where to show inline photo thumbnails") },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        PhotoLayout.entries.forEachIndexed { index, layout ->
                            SegmentedButton(
                                selected = photoLayout == layout,
                                onClick = { viewModel.setPhotoLayout(layout) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PhotoLayout.entries.size,
                                ),
                                label = {
                                    Text(
                                        when (layout) {
                                            PhotoLayout.ABOVE -> "Above"
                                            PhotoLayout.BELOW -> "Below"
                                            PhotoLayout.LEFT -> "Left"
                                        }
                                    )
                                },
                            )
                        }
                    }
                },
            )
            Divider()
            Text(
                "Sync",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Background sync") },
                supportingContent = { Text("Messages are synced every 15 minutes in the background") },
            )
        }
    }
}
