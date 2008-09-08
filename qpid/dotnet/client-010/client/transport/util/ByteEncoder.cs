using System;
using System.Collections.Generic;
using System.Text;

namespace org.apache.qpid.transport.util
{
    public static class ByteEncoder
    {
        #region Endian conversion helper routines
        /// <summary>
        /// Returns the value encoded in Big Endian (PPC, XDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Big-endian encoded value.</returns>
        public static Int32 GetBigEndian(Int32 value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return swapByteOrder(value);
            }
            else
            {
                return value;
            }
        }

        /// <summary>
        /// Returns the value encoded in Big Endian (PPC, XDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Big-endian encoded value.</returns>
        public static UInt16 GetBigEndian(UInt16 value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return swapByteOrder(value);
            }
            else
            {
                return value;
            }
        }

        /// <summary>
        /// Returns the value encoded in Big Endian (PPC, XDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Big-endian encoded value.</returns>
        public static UInt32 GetBigEndian(UInt32 value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return swapByteOrder(value);
            }
            else
            {
                return value;
            }
        }

        /// <summary>
        /// Returns the value encoded in Big Endian (PPC, XDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Big-endian encoded value.</returns>
        public static Double GetBigEndian(Double value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return swapByteOrder(value);
            }
            else
            {
                return value;
            }
        }

        /// <summary>
        /// Returns the value encoded in Little Endian (x86, NDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Little-endian encoded value.</returns>
        public static Int32 GetLittleEndian(Int32 value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return value;
            }
            else
            {
                return swapByteOrder(value);
            }
        }

        /// <summary>
        /// Returns the value encoded in Little Endian (x86, NDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Little-endian encoded value.</returns>
        public static UInt32 GetLittleEndian(UInt32 value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return value;
            }
            else
            {
                return swapByteOrder(value);
            }
        }

        /// <summary>
        /// Returns the value encoded in Little Endian (x86, NDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Little-endian encoded value.</returns>
        public static UInt16 GetLittleEndian(UInt16 value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return value;
            }
            else
            {
                return swapByteOrder(value);
            }
        }

        /// <summary>
        /// Returns the value encoded in Little Endian (x86, NDR) format.
        /// </summary>
        /// <param name="value">Value to encode.</param>
        /// <returns>Little-endian encoded value.</returns>
        public static Double GetLittleEndian(Double value)
        {
            if (BitConverter.IsLittleEndian)
            {
                return value;
            }
            else
            {
                return swapByteOrder(value);
            }
        }

        /// <summary>
        /// Swaps the Byte order of an <see cref="Int32"/>.
        /// </summary>
        /// <param name="value"><see cref="Int32"/> to swap the bytes of.</param>
        /// <returns>Byte order swapped <see cref="Int32"/>.</returns>
        private static Int32 swapByteOrder(Int32 value)
        {
            Int32 swapped = (Int32)((0x000000FF) & (value >> 24)
                                     | (0x0000FF00) & (value >> 8)
                                     | (0x00FF0000) & (value << 8)
                                     | (0xFF000000) & (value << 24));
            return swapped;
        }

        /// <summary>
        /// Swaps the byte order of a <see cref="UInt16"/>.
        /// </summary>
        /// <param name="value"><see cref="UInt16"/> to swap the bytes of.</param>
        /// <returns>Byte order swapped <see cref="UInt16"/>.</returns>
        private static UInt16 swapByteOrder(UInt16 value)
        {
            return (UInt16)((0x00FF & (value >> 8))
                             | (0xFF00 & (value << 8)));
        }

        /// <summary>
        /// Swaps the byte order of a <see cref="UInt32"/>.
        /// </summary>
        /// <param name="value"><see cref="UInt32"/> to swap the bytes of.</param>
        /// <returns>Byte order swapped <see cref="UInt32"/>.</returns>
        private static UInt32 swapByteOrder(UInt32 value)
        {
            UInt32 swapped = ((0x000000FF) & (value >> 24)
                             | (0x0000FF00) & (value >> 8)
                             | (0x00FF0000) & (value << 8)
                             | (0xFF000000) & (value << 24));
            return swapped;
        }

        /// <summary>
        /// Swaps the byte order of a <see cref="Double"/> (double precision IEEE 754)
        /// </summary>
        /// <param name="value"><see cref="Double"/> to swap.</param>
        /// <returns>Byte order swapped <see cref="Double"/> value.</returns>
        private static Double swapByteOrder(Double value)
        {
            Byte[] buffer = BitConverter.GetBytes(value);
            Array.Reverse(buffer, 0, buffer.Length);
            return BitConverter.ToDouble(buffer, 0);
        }
        #endregion
    }

}
