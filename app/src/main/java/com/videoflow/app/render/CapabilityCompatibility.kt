package com.videoflow.app.render

import com.videoflow.app.domain.export.ExportProblem
import com.videoflow.app.domain.export.ExportWarning

/** Internal helper used when encoder capability checks are intentionally bypassed for packet-copy. */
fun ExportCapabilityResult(
    warnings: List<ExportWarning>,
    problems: List<ExportProblem>
): CapabilityPreflight = CapabilityPreflight(selectedEncoder = null, warnings = warnings, problems = problems)
