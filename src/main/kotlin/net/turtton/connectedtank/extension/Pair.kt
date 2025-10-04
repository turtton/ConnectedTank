package net.turtton.connectedtank.extension

import com.mojang.datafixers.util.Pair as MojangPair
import com.mojang.serialization.Codec

fun <A, B> MojangPair<A, B>.toKotlinPair(): Pair<A, B> = Pair(first, second)
fun <A, B> Pair<A, B>.toMojangPair(): MojangPair<A, B> = MojangPair(first, second)

fun <A, B> Codec<MojangPair<A, B>>.toKotlinPairCodec(): Codec<Pair<A, B>> = xmap({ it.toKotlinPair() }, { it.toMojangPair() })
