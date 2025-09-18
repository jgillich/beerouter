/**
 * Processor for Voice Hints
 *
 * @author ab
 */
package btools.router

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VoiceHintProcessor // this.catchingRange = catchingRange;
    (
    catchingRange: Double, // private double catchingRange; // range to catch angles and merge turns
    private val explicitRoundabouts: Boolean, private val transportMode: Int
) {
    var SIGNIFICANT_ANGLE: Double = 22.5
    var INTERNAL_CATCHING_RANGE_NEAR: Double = 2.0
    var INTERNAL_CATCHING_RANGE_WIDE: Double = 10.0

    private fun sumNonConsumedWithinCatchingRange(
        inputs: MutableList<VoiceHint>,
        offset: Int,
        range: Double
    ): Float {
        var offset = offset
        var distance = 0.0
        var angle = 0f
        while (offset >= 0 && distance < range) {
            val input = inputs.get(offset--)
            if (input.turnAngleConsumed || input.command == VoiceHint.Companion.BL || input.command == VoiceHint.Companion.END) {
                break
            }
            angle += input.goodWay!!.turnangle
            distance += input.goodWay!!.linkdist.toDouble()
            input.turnAngleConsumed = true
        }
        return angle
    }


    /**
     * process voice hints. Uses VoiceHint objects
     * for both input and output. Input is in reverse
     * order (from target to start), but output is
     * returned in travel-direction and only for
     * those nodes that trigger a voice hint.
     *
     *
     * Input objects are expected for every segment
     * of the track, also for those without a junction
     *
     *
     * VoiceHint objects in the output list are enriched
     * by the voice-command, the total angle and the distance
     * to the next hint
     *
     * @param inputs tracknodes, un reverse order
     * @return voice hints, in forward order
     */
    fun process(inputs: MutableList<VoiceHint>): MutableList<VoiceHint> {
        val results: MutableList<VoiceHint> = ArrayList<VoiceHint>()
        var distance = 0.0
        var roundAboutTurnAngle = 0f // sums up angles in roundabout

        var roundaboutExit = 0
        var roundaboudStartIdx = -1

        for (hintIdx in inputs.indices) {
            val input = inputs.get(hintIdx)

            if (input.command == VoiceHint.Companion.BL) {
                results.add(input)
                continue
            }

            val turnAngle = input.goodWay!!.turnangle
            if (hintIdx != 0) distance += input.goodWay!!.linkdist.toDouble()
            //  System.out.println("range " + distance);
            val currentPrio = input.goodWay!!.priorityclassifier
            val oldPrio = input.oldWay!!.priorityclassifier
            val minPrio = min(oldPrio, currentPrio)

            val isLink2Highway = input.oldWay!!.isLinktType && !input.goodWay!!.isLinktType
            val isHighway2Link = !input.oldWay!!.isLinktType && input.goodWay!!.isLinktType

            if (explicitRoundabouts && input.oldWay!!.isRoundabout) {
                if (roundaboudStartIdx == -1) roundaboudStartIdx = hintIdx
                roundAboutTurnAngle += sumNonConsumedWithinCatchingRange(
                    inputs,
                    hintIdx,
                    INTERNAL_CATCHING_RANGE_NEAR
                )
                if (roundaboudStartIdx == hintIdx) {
                    if (input.badWays != null) {
                        // remove goodWay
                        roundAboutTurnAngle -= input.goodWay!!.turnangle
                        // add a badWay
                        for (badWay in input.badWays) {
                            if (!badWay.isBadOneway) roundAboutTurnAngle += badWay.turnangle
                        }
                    }
                }
                var isExit = roundaboutExit == 0 // exit point is always exit
                if (input.badWays != null) {
                    for (badWay in input.badWays) {
                        if (!badWay.isBadOneway &&
                            badWay.isGoodForCars
                        ) {
                            isExit = true
                        }
                    }
                }
                if (isExit) {
                    roundaboutExit++
                }
                continue
            }
            if (roundaboutExit > 0) {
                //roundAboutTurnAngle += sumNonConsumedWithinCatchingRange(inputs, hintIdx);
                //double startTurn = (roundaboudStartIdx != -1 ? inputs.get(roundaboudStartIdx + 1).goodWay.turnangle : turnAngle);
                input.angle = roundAboutTurnAngle
                input.goodWay!!.turnangle = roundAboutTurnAngle
                input.distanceToNext = distance
                input.turnAngleConsumed = true
                //input.roundaboutExit = startTurn < 0 ? roundaboutExit : -roundaboutExit;
                input.exitNumber = if (roundAboutTurnAngle < 0) roundaboutExit else -roundaboutExit
                var tmpangle = 0f
                val tmpRndAbt = VoiceHint()
                tmpRndAbt.badWays = ArrayList<MessageData>()
                for (i in hintIdx - 1 downTo roundaboudStartIdx + 1) {
                    val vh = inputs.get(i)
                    tmpangle += inputs.get(i).goodWay!!.turnangle
                    if (vh.badWays != null) {
                        for (badWay in vh.badWays) {
                            if (!badWay.isBadOneway) {
                                val md = MessageData()
                                md.linkdist = vh.goodWay!!.linkdist
                                md.priorityclassifier = vh.goodWay!!.priorityclassifier
                                md.turnangle = tmpangle
                                tmpRndAbt.badWays!!.add(md)
                            }
                        }
                    }
                }
                distance = 0.0

                input.badWays = tmpRndAbt.badWays

                results.add(input)
                roundAboutTurnAngle = 0f
                roundaboutExit = 0
                roundaboudStartIdx = -1
                continue
            }
            var maxPrioAll = -1 // max prio of all detours
            var maxPrioCandidates = -1 // max prio of real candidates

            var maxAngle = -180f
            var minAngle = 180f
            var minAbsAngeRaw = 180f

            var isBadwayLink = false

            if (input.badWays != null) {
                for (badWay in input.badWays) {
                    val badPrio = badWay.priorityclassifier
                    val badTurn = badWay.turnangle
                    if (badWay.isLinktType) {
                        isBadwayLink = true
                    }
                    val isBadHighway2Link = !input.oldWay!!.isLinktType && badWay.isLinktType

                    if (badPrio > maxPrioAll && !isBadHighway2Link) {
                        maxPrioAll = badPrio
                        input.maxBadPrio = max(input.maxBadPrio, badPrio)
                    }

                    if (badPrio < minPrio) {
                        continue  // ignore low prio ways
                    }

                    if (badWay.isBadOneway) {
                        if (minAbsAngeRaw == 180f) minAbsAngeRaw =
                            turnAngle // disable hasSomethingMoreStraight

                        continue  // ignore wrong oneways
                    }

                    if (abs(badTurn) - abs(turnAngle) > 80f) {
                        if (minAbsAngeRaw == 180f) minAbsAngeRaw =
                            turnAngle // disable hasSomethingMoreStraight

                        continue  // ways from the back should not trigger a slight turn
                    }

                    if (badWay.costfactor < 20f && abs(badTurn) < minAbsAngeRaw) {
                        minAbsAngeRaw = abs(badTurn)
                    }

                    if (badPrio > maxPrioCandidates) {
                        maxPrioCandidates = badPrio
                        input.maxBadPrio = max(input.maxBadPrio, badPrio)
                    }
                    if (badTurn > maxAngle) {
                        maxAngle = badTurn
                    }
                    if (badTurn < minAngle) {
                        minAngle = badTurn
                    }
                }
            }

            // boolean hasSomethingMoreStraight = (Math.abs(turnAngle) - minAbsAngeRaw) > 20.;
            val hasSomethingMoreStraight =
                (abs(turnAngle) - minAbsAngeRaw) > 20.0 && input.badWays != null // && !ignoreBadway;

            // unconditional triggers are all junctions with
            // - higher detour prios than the minimum route prio (except link->highway junctions)
            // - or candidate detours with higher prio then the route exit leg
            val unconditionalTrigger = hasSomethingMoreStraight ||
                    (maxPrioAll > minPrio && !isLink2Highway) ||
                    (maxPrioCandidates > currentPrio) ||
                    VoiceHint.Companion.is180DegAngle(turnAngle) ||
                    (!isHighway2Link && isBadwayLink && abs(turnAngle) > 5f) ||
                    (isHighway2Link && !isBadwayLink && abs(turnAngle) < 5f)

            // conditional triggers (=real turning angle required) are junctions
            // with candidate detours equal in priority than the route exit leg
            val conditionalTrigger = maxPrioCandidates >= minPrio

            if (unconditionalTrigger || conditionalTrigger) {
                input.angle = turnAngle
                input.calcCommand()
                val isStraight = input.command == VoiceHint.Companion.C
                input.needsRealTurn = (!unconditionalTrigger) && isStraight

                // check for KR/KL
                if (abs(turnAngle) > 5.0) { // don't use too small angles
                    if (maxAngle < turnAngle && maxAngle > turnAngle - 45f - (max(turnAngle, 0f))) {
                        input.command = VoiceHint.Companion.KR
                    }
                    if (minAngle > turnAngle && minAngle < turnAngle + 45f - (min(turnAngle, 0f))) {
                        input.command = VoiceHint.Companion.KL
                    }
                }

                input.angle =
                    sumNonConsumedWithinCatchingRange(inputs, hintIdx, INTERNAL_CATCHING_RANGE_WIDE)
                input.distanceToNext = distance
                distance = 0.0
                results.add(input)
            }
            if (results.size > 0 && distance < INTERNAL_CATCHING_RANGE_NEAR) { //catchingRange
                results.get(results.size - 1).angle += sumNonConsumedWithinCatchingRange(
                    inputs,
                    hintIdx,
                    INTERNAL_CATCHING_RANGE_NEAR
                )
            }
        }

        // go through the hint list again in reverse order (=travel direction)
        // and filter out non-significant hints and hints too close to its predecessor
        val results2: MutableList<VoiceHint> = ArrayList<VoiceHint>()
        var i = results.size
        while (i > 0) {
            var hint = results.get(--i)
            if (hint.command == 0) {
                hint.calcCommand()
            }
            if (hint.command == VoiceHint.Companion.END) {
                results2.add(hint)
                continue
            }
            if (!(hint.needsRealTurn && (hint.command == VoiceHint.Companion.C || hint.command == VoiceHint.Companion.BL))) {
                var dist = hint.distanceToNext
                // sum up other hints within the catching range (e.g. 40m)
                while (dist < INTERNAL_CATCHING_RANGE_NEAR && i > 0) {
                    val h2 = results.get(i - 1)
                    dist = h2.distanceToNext
                    hint.distanceToNext += dist
                    hint.angle += h2.angle
                    i--
                    if (h2.isRoundabout) { // if we hit a roundabout, use that as the trigger
                        h2.angle = hint.angle
                        hint = h2
                        break
                    }
                }

                if (!explicitRoundabouts) {
                    hint.exitNumber = 0 // use an angular hint instead
                }
                hint.calcCommand()
                results2.add(hint)
            } else if (hint.command == VoiceHint.Companion.BL) {
                results2.add(hint)
            } else {
                if (results2.size > 0) results2.get(results2.size - 1)!!.distanceToNext += hint.distanceToNext
            }
        }
        return results2
    }

    fun postProcess(
        inputs: MutableList<VoiceHint>,
        catchingRange: Double,
        minRange: Double
    ): MutableList<VoiceHint> {
        val results: MutableList<VoiceHint> = ArrayList()
        val distance = 0.0
        var inputLast: VoiceHint? = null
        var inputLastSaved: VoiceHint? = null
        var hintIdx = 0
        while (hintIdx < inputs.size) {
            val input = inputs.get(hintIdx)
            var nextInput: VoiceHint? = null
            if (hintIdx + 1 < inputs.size) {
                nextInput = inputs.get(hintIdx + 1)
            }

            if (input.command == VoiceHint.Companion.BL) {
                results.add(input)
                hintIdx++
                continue
            }

            if (nextInput == null) {
                if (input.command == VoiceHint.Companion.END) {
                    hintIdx++
                    continue
                } else if ((input.command == VoiceHint.Companion.C || input.command == VoiceHint.Companion.KR || input.command == VoiceHint.Companion.KL)
                    && !input.goodWay!!.isLinktType
                ) {
                    if (((abs(input.lowerBadWayAngle) < 35f ||
                                input.higherBadWayAngle < 35f)
                                || input.goodWay!!.priorityclassifier < input.maxBadPrio)
                        && (inputLastSaved != null && inputLastSaved.distanceToNext > minRange)
                        && (input.distanceToNext > minRange)
                    ) {
                        results.add(input)
                    } else {
                        if (inputLast != null) { // when drop add distance to last
                            inputLast.distanceToNext += input.distanceToNext
                        }
                        hintIdx++
                        continue
                    }
                } else {
                    results.add(input)
                }
            } else {
                if ((inputLastSaved != null && inputLastSaved.distanceToNext > catchingRange) || input.distanceToNext > catchingRange) {
                    if ((input.command == VoiceHint.Companion.C || input.command == VoiceHint.Companion.KR || input.command == VoiceHint.Companion.KL)
                        && !input.goodWay!!.isLinktType
                    ) {
                        if (((abs(input.lowerBadWayAngle) < 35f ||
                                    input.higherBadWayAngle < 35f)
                                    || input.goodWay!!.priorityclassifier < input.maxBadPrio)
                            && (inputLastSaved != null && inputLastSaved.distanceToNext > minRange)
                            && (input.distanceToNext > minRange)
                        ) {
                            // add only on prio
                            results.add(input)
                            inputLastSaved = input
                        } else {
                            if (inputLastSaved != null) { // when drop add distance to last
                                inputLastSaved.distanceToNext += input.distanceToNext
                            }
                        }
                    } else if ((input.goodWay!!.priorityclassifier == 29 && input.maxBadPrio == 30) &&
                        checkForNextNoneMotorway(inputs, hintIdx, 3)
                    ) {
                        // leave motorway
                        if (input.command == VoiceHint.Companion.KR || input.command == VoiceHint.Companion.TSLR) {
                            input.command = VoiceHint.Companion.ER
                        } else if (input.command == VoiceHint.Companion.KL || input.command == VoiceHint.Companion.TSLL) {
                            input.command = VoiceHint.Companion.EL
                        }
                        results.add(input)
                        inputLastSaved = input
                    } else {
                        // add all others
                        // ignore motorway / primary continue
                        if (((input.goodWay!!.priorityclassifier != 28) &&
                                    (input.goodWay!!.priorityclassifier != 30) &&
                                    (input.goodWay!!.priorityclassifier != 26))
                            || input.isRoundabout
                            || abs(input.angle) > 21f
                        ) {
                            results.add(input)
                            inputLastSaved = input
                        } else {
                            if (inputLastSaved != null) { // when drop add distance to last
                                inputLastSaved.distanceToNext += input.distanceToNext
                            }
                        }
                    }
                } else if (input.distanceToNext < catchingRange) {
                    var dist = input.distanceToNext
                    var angles = input.angle
                    val i = 1
                    var save = false

                    dist += nextInput.distanceToNext
                    angles += nextInput.angle

                    if ((input.command == VoiceHint.Companion.C || input.command == VoiceHint.Companion.KR || input.command == VoiceHint.Companion.KL)
                        && !input.goodWay!!.isLinktType
                    ) {
                        if (input.goodWay!!.priorityclassifier < input.maxBadPrio) {
                            if (inputLastSaved != null && inputLastSaved.command != VoiceHint.Companion.C && (inputLastSaved != null && inputLastSaved.distanceToNext > minRange)
                                && transportMode != VoiceHintList.Companion.TRANS_MODE_CAR
                            ) {
                                // add when straight and not linktype
                                // and last vh not straight
                                save = true
                                // remove when next straight and not linktype
                                if (nextInput != null && nextInput.command == VoiceHint.Companion.C && !nextInput.goodWay!!.isLinktType) {
                                    input.distanceToNext += nextInput.distanceToNext
                                    hintIdx++
                                }
                            }
                        } else {
                            if (inputLastSaved != null) { // when drop add distance to last
                                inputLastSaved.distanceToNext += input.distanceToNext
                            }
                        }
                    } else if ((input.goodWay!!.priorityclassifier == 29 && input.maxBadPrio == 30)) {
                        // leave motorway
                        if (input.command == VoiceHint.Companion.KR || input.command == VoiceHint.Companion.TSLR) {
                            input.command = VoiceHint.Companion.ER
                        } else if (input.command == VoiceHint.Companion.KL || input.command == VoiceHint.Companion.TSLL) {
                            input.command = VoiceHint.Companion.EL
                        }
                        save = true
                    } else if (VoiceHint.Companion.is180DegAngle(input.angle)) {
                        // add u-turn, 180 degree
                        save = true
                    } else if (transportMode == VoiceHintList.Companion.TRANS_MODE_CAR && abs(angles) > 180 - SIGNIFICANT_ANGLE) {
                        // add when inc car mode and u-turn, collects e.g. two left turns in range
                        input.angle = angles
                        input.calcCommand()
                        input.distanceToNext += nextInput.distanceToNext
                        save = true
                        hintIdx++
                    } else if (abs(angles) < SIGNIFICANT_ANGLE && input.distanceToNext < minRange) {
                        input.angle = angles
                        input.calcCommand()
                        input.distanceToNext += nextInput.distanceToNext
                        save = true
                        hintIdx++
                    } else if (abs(input.angle) > SIGNIFICANT_ANGLE) {
                        // add when angle above 22.5 deg
                        save = true
                    } else if (abs(input.angle) < SIGNIFICANT_ANGLE) {
                        // add when angle below 22.5 deg ???
                        // save = true;
                    } else {
                        // otherwise ignore but add distance to next
                        if (nextInput != null) { // when drop add distance to last
                            nextInput.distanceToNext += input.distanceToNext
                        }
                        save = false
                    }

                    if (save) {
                        results.add(input) // add when last
                        inputLastSaved = input
                    }
                } else {
                    results.add(input)
                    inputLastSaved = input
                }
            }
            inputLast = input
            hintIdx++
        }
        if (results.size > 0) {
            // don't use END tag
            if (results.get(results.size - 1)!!.command == VoiceHint.Companion.END) results.removeAt(
                results.size - 1
            )
        }

        return results
    }

    fun checkForNextNoneMotorway(
        inputs: MutableList<VoiceHint>,
        offset: Int,
        testsize: Int
    ): Boolean {
        var i = 1
        while (i < testsize + 1 && offset + i < inputs.size) {
            val prio = inputs.get(offset + i).goodWay!!.priorityclassifier
            if (prio < 29) return true
            if (prio == 30) return false
            i++
        }
        return false
    }
}
