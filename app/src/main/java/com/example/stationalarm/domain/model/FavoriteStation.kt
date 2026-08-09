package com.example.stationalarm.domain.model

const val MAX_FAVORITE_STATIONS = 5

/** お気に入りの表示順を保った、タイル用の駅モデル。 */
data class FavoriteStation(
    val slot: Int,
    val name: String
) {
    // 駅の置換時にIDも変えて、タイル描画側が古いActionを再利用しないようにする
    val clickableId: String = "favorite_${slot}_${name.hashCode().toUInt().toString(16)}"

    val tileLabel: String = name
        .removeSuffix("駅")
        .let { normalized ->
            if (normalized.length <= MAX_TILE_LABEL_LENGTH) normalized
            else normalized.take(MAX_TILE_LABEL_LENGTH - 1) + "…"
        }

    companion object {
        private const val MAX_TILE_LABEL_LENGTH = 5
    }
}

object FavoriteStationDefaults {
    val names = listOf("西千葉", "お茶の水", "代々木", "新宿", "錦糸町")
}

fun List<String>.toFavoriteStations(): List<FavoriteStation> =
    take(MAX_FAVORITE_STATIONS).mapIndexed { index, name ->
        FavoriteStation(slot = index, name = name)
    }

/** 5件を2列・2列・1列で、登録順のまま配置する。 */
fun List<FavoriteStation>.toTileRows(): List<List<FavoriteStation>> = chunked(2)

/** 正常な置換だけ新しい5件を返し、重複・空文字・範囲外は null を返す。 */
fun List<String>.withFavoriteReplaced(index: Int, stationName: String): List<String>? {
    val normalizedName = stationName.trim()
    if (
        size != MAX_FAVORITE_STATIONS ||
        index !in indices ||
        normalizedName.isEmpty() ||
        normalizedName in this
    ) {
        return null
    }

    return toMutableList().apply { this[index] = normalizedName }
}
