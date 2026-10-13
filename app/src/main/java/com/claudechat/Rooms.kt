package com.claudechat

/** Room backgrounds for the agent video-call tiles (drawable room_<name>, made by tools/gen_rooms.py). */
val ROOM_DRAWABLES: Map<String, Int> = linkedMapOf(
    "living" to R.drawable.room_living,
    "kitchen" to R.drawable.room_kitchen,
    "bedroom" to R.drawable.room_bedroom,
    "office" to R.drawable.room_office,
    "library" to R.drawable.room_library,
    "garden" to R.drawable.room_garden,
    "beach" to R.drawable.room_beach,
    "cabin" to R.drawable.room_cabin,
    "garage" to R.drawable.room_garage,
    "loft" to R.drawable.room_loft,
    "cafe" to R.drawable.room_cafe,
    "classroom" to R.drawable.room_classroom,
    "gym" to R.drawable.room_gym,
    "studio" to R.drawable.room_studio,
    "server" to R.drawable.room_server,
    "lab" to R.drawable.room_lab,
    "balcony" to R.drawable.room_balcony,
    "rooftop" to R.drawable.room_rooftop,
    "bathroom" to R.drawable.room_bathroom,
    "kids" to R.drawable.room_kids,
    "music" to R.drawable.room_music,
    "workshop" to R.drawable.room_workshop,
    "greenhouse" to R.drawable.room_greenhouse,
    "arcade" to R.drawable.room_arcade,
    "space" to R.drawable.room_space,
    "train" to R.drawable.room_train,
    "hut" to R.drawable.room_hut,
    "attic" to R.drawable.room_attic,
    "shop" to R.drawable.room_shop,
    "hallway" to R.drawable.room_hallway,
)

val ROOMS: List<String> = ROOM_DRAWABLES.keys.toList()

/** Drawable id for a room name; an unknown name gets the first room. */
fun roomRes(name: String): Int = ROOM_DRAWABLES[name] ?: ROOM_DRAWABLES.getValue(ROOMS[0])

/** The same room for the same agent every time (stable hash of its id). */
fun roomFor(agentId: String): String = ROOMS[Math.floorMod(agentId.hashCode(), ROOMS.size)]
