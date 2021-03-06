/*
 * This file is part of MESSIF library.
 *
 * MESSIF library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MESSIF library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package messif.utility;

/**
 * Provides a convertor from class F to class T.
 *
 * @param <F> the source class of this convertor
 * @param <T> the destination class of this convertor
 * 
 * @author Michal Batko, Masaryk University, Brno, Czech Republic, batko@fi.muni.cz
 * @author Vlastislav Dohnal, Masaryk University, Brno, Czech Republic, dohnal@fi.muni.cz
 * @author David Novak, Masaryk University, Brno, Czech Republic, david.novak@fi.muni.cz
 */
public interface Convertor<F, T> {
    /**
     * Converts {@code value} to another type.
     * @param value the value to convert
     * @return the converted value
     * @throws Exception if there was a conversion error
     */
    public T convert(F value) throws Exception;

    /**
     * Returns the class that this convertor converts to.
     * @return the class that this convertor converts to
     */
    public Class<? extends T> getDestinationClass();
}
