/*
 *  This file is part of MESSIF library.
 *
 *  MESSIF library is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  MESSIF library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MESSIF library.  If not, see <http://www.gnu.org/licenses/>.
 */
package messif.utility;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Basic implementation of the {@link Parametric} interface on encapsulated {@link Map}.
 * Note that this class can be used as wrapper for {@link Map}.
 *
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public class ParametricBase implements Parametric {
    /** Class serial id for serialization */
    private static final long serialVersionUID = 1L;

    /** Encapsulated {@link Map} that provides the parameter values */
    private final Map<String, ? extends Object> map;

    /**
     * Creates a new instance of ParametricBase backed-up by the given map with parameters.
     * @param map the map that provides the parameter values
     */
    public ParametricBase(Map<String, ? extends Object> map) {
        this.map = map;
    }

    @Override
    public int getParameterCount() {
        return map != null ? map.size() : 0;
    }

    @Override
    public Collection<String> getParameterNames() {
        if (map == null)
            return Collections.emptyList();
        return Collections.unmodifiableCollection(map.keySet());
    }

    @Override
    public boolean containsParameter(String name) {
        return map != null && map.containsKey(name);
    }

    @Override
    public Object getParameter(String name) {
        return map != null ? map.get(name) : null;
    }

    @Override
    public Object getRequiredParameter(String name) throws IllegalArgumentException {
        Object parameter = getParameter(name);
        if (parameter == null)
            throw new IllegalArgumentException("The parameter '" + name + "' is not set");
        return parameter;
    }

    @Override
    public <T> T getRequiredParameter(String name, Class<? extends T> parameterClass) throws IllegalArgumentException, ClassCastException {
        return parameterClass.cast(getRequiredParameter(name));
    }

    @Override
    public <T> T getParameter(String name, Class<? extends T> parameterClass, T defaultValue) {
        Object value = getParameter(name);
        return value != null && parameterClass.isInstance(value) ? parameterClass.cast(value) : defaultValue; // This cast IS checked by isInstance
    }

    @Override
    public <T> T getParameter(String name, Class<? extends T> parameterClass) {
        return getParameter(name, parameterClass, null);
    }

    @Override
    public Map<String, ? extends Object> getParameterMap() {
        if (map == null)
            return Collections.emptyMap();
        return Collections.unmodifiableMap(map);
    }

}
