package org.openfactor.consumer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.openfactor.app.AppData
import org.openfactor.app.DataCodec
import org.openfactor.app.WorkoutCommands

/** Compiled on the main classpath with only project(:core), without test-library dependencies. */
fun consumePublicDependencies(scope: CoroutineScope, write: (JSONObject) -> Unit): StateFlow<AppData> {
    val commands = WorkoutCommands(AppData(), scope, write = { write(DataCodec.document(it)) })
    return commands.data
}
