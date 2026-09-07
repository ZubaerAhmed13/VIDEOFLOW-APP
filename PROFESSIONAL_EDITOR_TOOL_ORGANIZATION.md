# Professional tool organization

The selected-video horizontal rail now includes standard editing plus Audio, Effects, Enhance, AI Tools, Precise Trim and Canvas. The floating Precise Trim and AI buttons have been removed. Existing audio/text/image context rails remain intact. Advanced tools have one explicit `ProfessionalEditorTool` session; they do not introduce unrelated open/close booleans.

The existing Home, preview, transport, timeline and editor design remain the foundation. Tools open a bounded bottom panel in portrait and a side inspector on wide layouts. Panels have preview and Cancel/Done; persistent sliders do not write Room per gesture. A separate transient Before/After control supports visual comparison.

AI Watermark retains local tracking, models, Apply/Update/toggles/removal. Added controls include exact range fields, zoom-local handles, Set Start/End, seek/range navigation, multiple effects, same-time Before/After, manual position/size corrections and fast/detailed preview. Final output always uses the final model at original ROI resolution.

Tool previews stay visible while controls scroll. AI Studio uses Select, Time, Track, Preview and Apply stages. Effect indicators have independent 48-dp rows and open their exact effect. The mask image and handles share a fitted aspect-preserving box. Product-panel instrumentation captures Effects, Enhance and AI Preview screenshots. Comprehensive physical TalkBack and phone/tablet/landscape usability certification remains a Step-5 review item. Original VideoFlow visuals are used; no InShot code or assets are copied.
