/*
    Open Aviation Map
    Copyright (C) 2012 Ákos Maróy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package hu.tyrell.openaviationmap.model;

/**
 * A boundary type described as a circle of some radius around a center
 * point.
 */
public class Circle implements Boundary {
    /**
     * The center point of the circle.
     */
    private Point center;

    /**
     * The radius of the circle.
     */
    private Distance radius;

    /**
     * Return the type of this boundary.
     *
     * @return Circle
     */
    @Override
    public Type getType() {
        return Type.CIRCLE;
    }

    /**
     * @return the center
     */
    public Point getCenter() {
        return center;
    }

    /**
     * @param center the center to set
     */
    public void setCenter(Point center) {
        this.center = center;
    }

    /**
     * @return the radius
     */
    public Distance getRadius() {
        return radius;
    }

    /**
     * @param radius the radius to set
     */
    public void setRadius(Distance radius) {
        this.radius = radius;
    }

}