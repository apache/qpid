namespace Qpid.Buffer
{
   /// <summary>
   /// Allocates <see cref="ByteBuffer"/>'s and manages them. Please 
   /// implement this interface if you need more advanced memory management scheme
   /// </summary>
   public interface IByteBufferAllocator
   {
      /// <summary>
      /// Returns the buffer which is capable of the specified size.
      /// </summary>
      /// <param name="capacity">The capacity of the buffer</param>
      /// <param name="direct">true to get a direct buffer, false to get a heap buffer</param>
      ByteBuffer Allocate(int capacity, bool direct);

      /// <summary>
      /// Wraps the specified buffer
      /// </summary>
      /// <param name="nioBuffer">fixed byte buffer</param>
      /// <returns>The wrapped buffer</returns>
      ByteBuffer Wrap(FixedByteBuffer nioBuffer);


      /// <summary>
      /// Dispose of this allocator.
      /// </summary>
      void Dispose();
   }
}