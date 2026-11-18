param(
    [string]$SourceRoot = (Join-Path $PSScriptRoot '..\docs\assets\ui22\source'),
    [string]$OutputRoot = (Join-Path $PSScriptRoot '..\app\src\main\res\drawable-nodpi')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Sheet name, row, column, destination drawable. Crops deliberately omit the
# rasterized label so Android keeps the real, accessible and localized text.
$map = @(
    @('sheet_model.png',0,0,'ui22_flatten'), @('sheet_model.png',0,1,'ui22_delete'),
    @('sheet_model.png',0,2,'ui22_place_surface'), @('sheet_model.png',0,3,'ui22_align_axes'),
    @('sheet_model.png',1,0,'ui22_combine_parts'), @('sheet_model.png',1,1,'ui22_boolean'),
    @('sheet_model.png',1,2,'ui22_fill_bed'), @('sheet_model.png',1,3,'ui22_auto_orient'),
    @('sheet_model.png',2,0,'ui22_objects'), @('sheet_model.png',2,1,'ui22_duplicate'),
    @('sheet_model.png',2,2,'ui22_move_plate'), @('sheet_model.png',2,3,'ui22_split_parts'),
    @('sheet_model.png',3,0,'ui22_center'), @('sheet_model.png',3,1,'ui22_fit_volume'),
    @('sheet_model.png',3,2,'ui22_place_plate'), @('sheet_model.png',3,3,'ui22_place_object'),
    @('sheet_edit_paint.png',0,0,'ui22_smooth_mesh'), @('sheet_edit_paint.png',0,1,'ui22_cut'),
    @('sheet_edit_paint.png',0,2,'ui22_variable_layer_height'), @('sheet_edit_paint.png',0,3,'ui22_support_paint'),
    @('sheet_edit_paint.png',1,0,'ui22_seam_paint'), @('sheet_edit_paint.png',1,1,'ui22_fuzzy_paint'),
    @('sheet_edit_paint.png',1,2,'ui22_color_paint'), @('sheet_edit_paint.png',1,3,'ui22_emboss'),
    @('sheet_model_ops.png',0,0,'ui22_modifier_volume'), @('sheet_model_ops.png',0,1,'ui22_object_settings'),
    @('sheet_model_ops.png',0,2,'ui22_arrange_all'), @('sheet_model_ops.png',0,3,'ui22_primitives'),
    @('sheet_model_ops.png',1,0,'ui22_align_all'), @('sheet_model_ops.png',1,1,'ui22_make_independent'),
    @('sheet_model_ops.png',1,2,'ui22_select_all'), @('sheet_model_ops.png',1,3,'ui22_delete_all'),
    @('sheet_transform.png',0,0,'ui22_reset_rotation'), @('sheet_transform.png',0,1,'ui22_reset_scale'),
    @('sheet_transform.png',0,2,'ui22_undo'), @('sheet_transform.png',0,3,'ui22_redo'),
    @('sheet_transform.png',1,0,'ui22_move'), @('sheet_transform.png',1,1,'ui22_rotate'),
    @('sheet_transform.png',1,2,'ui22_scale'), @('sheet_transform.png',1,3,'ui22_simplify'),
    @('sheet_navigation.png',0,0,'ui22_selection'), @('sheet_navigation.png',0,1,'ui22_plate'),
    @('sheet_navigation.png',0,2,'ui22_file'), @('sheet_navigation.png',0,3,'ui22_model'),
    @('sheet_navigation.png',1,0,'ui22_edit'), @('sheet_navigation.png',1,1,'ui22_tools'),
    @('sheet_navigation.png',1,2,'ui22_slice'), @('sheet_navigation.png',1,3,'ui22_open_model'),
    @('sheet_navigation.png',2,0,'ui22_recent'), @('sheet_navigation.png',2,1,'ui22_export_3mf'),
    @('sheet_navigation.png',2,2,'ui22_profile_export'), @('sheet_navigation.png',2,3,'ui22_profile_import'),
    @('sheet_navigation.png',3,0,'ui22_calibration'), @('sheet_navigation.png',3,1,'ui22_quick_slice'),
    @('sheet_navigation.png',3,2,'ui22_smart_slice'), @('sheet_navigation.png',3,3,'ui22_slice_plate')
)

$loaded = @{}
foreach ($entry in $map) {
    $file,$row,$col,$dest = $entry
    if (-not $loaded.ContainsKey($file)) {
        $bitmap = [System.Drawing.Bitmap]::FromFile((Join-Path $SourceRoot $file))
        $loaded[$file] = $bitmap
    }
    $sheet = $loaded[$file]
    $gridCols = 4
    $gridRows = if ($sheet.Height -lt 1000) { 2 } else { 4 }
    $cellW = [double]$sheet.Width / $gridCols
    $cellH = [double]$sheet.Height / $gridRows
    $left = [int][Math]::Round($col * $cellW + $cellW * $(if ($gridRows -eq 2) { 0.08 } else { 0.13 }))
    $top = [int][Math]::Round($row * $cellH + $cellH * $(if ($gridRows -eq 2) { 0.04 } else { 0.09 }))
    $cropW = [int][Math]::Round($cellW * $(if ($gridRows -eq 2) { 0.84 } else { 0.80 }))
    $cropH = if ($gridRows -eq 2) { [int][Math]::Round($cellH * 0.59) } elseif ($row -eq 3) { [int][Math]::Round($cellH * 0.47) } else { [int][Math]::Round($cellH * 0.54) }
    $crop = New-Object System.Drawing.Bitmap($cropW, $cropH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($crop)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($sheet, (New-Object System.Drawing.Rectangle(0,0,$cropW,$cropH)), (New-Object System.Drawing.Rectangle($left,$top,$cropW,$cropH)), [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()
    $out = New-Object System.Drawing.Bitmap(256,256,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $scale = [Math]::Min(232.0 / $cropW, 232.0 / $cropH)
    $w = [int][Math]::Round($cropW * $scale); $h = [int][Math]::Round($cropH * $scale)
    $dx = [int](($out.Width - $w) / 2); $dy = [int](($out.Height - $h) / 2)
    $g.DrawImage($crop, (New-Object System.Drawing.Rectangle($dx,$dy,$w,$h)))
    $g.Dispose(); $crop.Dispose()
    $out.Save((Join-Path $OutputRoot ($dest + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
}
foreach ($sheet in $loaded.Values) { $sheet.Dispose() }
Write-Output "Generated $($map.Count) UI 2.2 action images."




