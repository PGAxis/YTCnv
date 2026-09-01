package com.pg_axis.ytcnv.side_pages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pg_axis.ytcnv.R

@Composable
fun SideScreenHeader(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.size(45.dp).padding(horizontal = 5.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.back),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        content()
    }
}