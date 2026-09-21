package com.rapidursa.companions;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.function.Predicate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathingLogic {

    /**
     * Finds a short collision-aware route through the local scene and returns a
     * buffered destination a few steps along it. Unlike the NPC-style helper,
     * this can temporarily move away from the target to get around walls and
     * scenery.
     */
    public static WorldArea calculatePathDestination(Client client, WorldArea start, WorldArea target,
                                                       int stopDistance, int stepsAhead,
                                                       Predicate<? super WorldPoint> extraCondition)
    {
        if (start == null || target == null || start.getPlane() != target.getPlane())
        {
            return null;
        }
        if (start.distanceTo(target) <= stopDistance)
        {
            return start;
        }

        final int searchRadius = 24;
        final int maxVisited = 2500;
        ArrayDeque<WorldArea> open = new ArrayDeque<>();
        Map<Long, Long> parents = new HashMap<>();
        Map<Long, WorldArea> areas = new HashMap<>();
        long startKey = pathKey(start.getX(), start.getY());
        open.add(start);
        parents.put(startKey, startKey);
        areas.put(startKey, start);

        WorldArea best = start;
        int bestDistance = start.distanceTo(target);
        WorldArea goal = null;
        int[] directions = {-1, 0, 1};

        while (!open.isEmpty() && parents.size() < maxVisited)
        {
            WorldArea current = open.removeFirst();
            if (current.distanceTo(target) <= stopDistance)
            {
                goal = current;
                break;
            }

            for (int dx : directions)
            {
                for (int dy : directions)
                {
                    if (dx == 0 && dy == 0)
                    {
                        continue;
                    }
                    int nextX = current.getX() + dx;
                    int nextY = current.getY() + dy;
                    if (Math.abs(nextX - start.getX()) > searchRadius
                            || Math.abs(nextY - start.getY()) > searchRadius)
                    {
                        continue;
                    }

                    long nextKey = pathKey(nextX, nextY);
                    if (parents.containsKey(nextKey)
                            || !current.canTravelInDirection(client.getTopLevelWorldView(), dx, dy, extraCondition))
                    {
                        continue;
                    }

                    WorldArea next = new WorldArea(nextX, nextY, start.getWidth(), start.getHeight(), start.getPlane());
                    long currentKey = pathKey(current.getX(), current.getY());
                    parents.put(nextKey, currentKey);
                    areas.put(nextKey, next);
                    open.addLast(next);

                    int distance = next.distanceTo(target);
                    if (distance < bestDistance)
                    {
                        best = next;
                        bestDistance = distance;
                    }
                }
            }
        }

        WorldArea destination = goal != null ? goal : best;
        if (destination.equals(start))
        {
            return start;
        }

        List<WorldArea> path = new ArrayList<>();
        long cursor = pathKey(destination.getX(), destination.getY());
        while (cursor != startKey)
        {
            path.add(areas.get(cursor));
            Long parent = parents.get(cursor);
            if (parent == null || parent == cursor)
            {
                return null;
            }
            cursor = parent;
        }
        Collections.reverse(path);
        return path.get(Math.min(Math.max(1, stepsAhead), path.size()) - 1);
    }

    private static long pathKey(int x, int y)
    {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    public static WorldArea calculateNextTravellingPoint(Client client, WorldArea start, WorldArea target, boolean stopAtMeleeDistance)
    {
        return calculateNextTravellingPoint(client,start, target, stopAtMeleeDistance, x -> true);
    }

    /**
     * Calculates the next area that will be occupied if this area attempts
     * to move toward it by using the normal NPC travelling pattern.
     *
     * @param client the client to calculate with
     * @param target the target area
     * @param stopAtMeleeDistance whether to stop at melee distance to the target
     * @param extraCondition an additional condition to perform when checking valid tiles,
     * 	                     such as performing a check for un-passable actors
     * @return the next occupied area
     */
    public static WorldArea calculateNextTravellingPoint(Client client,WorldArea start, WorldArea target, boolean stopAtMeleeDistance, Predicate<? super WorldPoint> extraCondition)
    {
        if (start.getPlane() != target.getPlane())
        {
            return null;
        }

        if (start.intersectsWith(target))
        {
            if (stopAtMeleeDistance)
            {
                // Movement is unpredictable when the NPC and actor stand on top of each other
                return null;
            }
            else
            {
                return start;
            }
        }

        int dx = target.getX() - start.getX();
        int dy = target.getY() - start.getY();
        int dxSig = Integer.signum(dx);
        int dySig = Integer.signum(dy);

        Point axisDistances = getAxisDistances(start,target);

        if (stopAtMeleeDistance && axisDistances.getX() + axisDistances.getY() == 1)
        {
            // NPC is in melee distance of target, so no movement is done
            return start;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, start.getX(), start.getY());
        if (lp == null ||
                lp.getSceneX() + dxSig < 0 || lp.getSceneX() + dxSig >= Constants.SCENE_SIZE ||
                lp.getSceneY() + dySig < 0 || lp.getSceneY() + dySig >= Constants.SCENE_SIZE)
        {
            // NPC is travelling out of the scene, so collision data isn't available
            return null;
        }

        if (stopAtMeleeDistance && axisDistances.getX() == 1 && axisDistances.getY() == 1)
        {
            // When it needs to stop at melee distance, it will only attempt
            // to travel along the x axis when it is standing diagonally
            // from the target
            if (start.canTravelInDirection(client.getTopLevelWorldView(), dxSig, 0, extraCondition))
            {
                return new WorldArea(start.getX() + dxSig, start.getY(), start.getWidth(), start.getHeight(), start.getPlane());
            }
        }
        else
        {
            if (start.canTravelInDirection(client.getTopLevelWorldView(), dxSig, dySig, extraCondition))
            {
                return new WorldArea(start.getX() + dxSig, start.getY() + dySig, start.getWidth(), start.getHeight(), start.getPlane());
            }
            else if (dx != 0 && start.canTravelInDirection(client.getTopLevelWorldView(), dxSig, 0, extraCondition))
            {
                return new WorldArea(start.getX() + dxSig, start.getY(), start.getWidth(), start.getHeight(), start.getPlane());
            }
            else if (dy != 0 && Math.max(Math.abs(dx), Math.abs(dy)) > 1 &&
                    start.canTravelInDirection(client.getTopLevelWorldView(), 0, dySig, extraCondition))
            {
                // Note that NPCs don't attempts to travel along the y-axis
                // if the target is <= 1 tile distance away
                return new WorldArea(start.getX(), start.getY() + dySig, start.getWidth(), start.getHeight(), start.getPlane());
            }
        }

        // The NPC is stuck
        return start;
    }

    private static Point getAxisDistances(WorldArea wa1,WorldArea wa2)
    {
        Point p1 = getComparisonPoint(wa1,wa2);
        Point p2 = getComparisonPoint(wa2,wa1);
        return new Point(Math.abs(p1.getX() - p2.getX()), Math.abs(p1.getY() - p2.getY()));
    }


    private static Point getComparisonPoint(WorldArea wa1,WorldArea wa2)
    {
        int x, y;
        if (wa2.getX() <= wa1.getX() )
        {
            x = wa1.getX() ;
        }
        else if (wa2.getX()  >= wa1.getX()  + wa1.getWidth() - 1)
        {
            x = wa1.getX()  + wa1.getWidth() - 1;
        }
        else
        {
            x = wa2.getX() ;
        }
        if (wa2.getY() <= wa1.getY())
        {
            y = wa1.getY();
        }
        else if (wa2.getY() >= wa1.getY() + wa1.getHeight() - 1)
        {
            y = wa1.getY() + wa1.getHeight() - 1;
        }
        else
        {
            y = wa2.getY();
        }
        return new Point(x, y);
    }



}
