using System;
using System.ServiceModel;
using System.Windows.Forms;
using org.apache.qpid.wcf.demo;
using org.apache.qpid.wcf.model;

namespace WindowsFormsBooking
{
    public partial class Form1 : Form
    {
        private ChannelFactory<IBooking> _fac;
        private readonly Order _order = new Order();
        private IBooking _calc;
         
        public Form1()
        {
            InitializeComponent();
            _calc = StartClient(new QpidBinding("192.168.1.14", 5673));
            _order.Type = "Default";
            _order.Price = 0;
        }

        public IBooking StartClient(System.ServiceModel.Channels.Binding binding)
        {
            IBooking res = null;
            try
            {
                Console.WriteLine("  Starting Client...");
                _fac = new ChannelFactory<IBooking>(binding, "soap.amqp:///Booking");
                _fac.Open();
                res = _fac.CreateChannel();
                Console.WriteLine("[DONE]");
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
            return res;
        }

        public void StopClient(IBooking client)
        {
            Console.WriteLine("  Stopping Client...");
            ((System.ServiceModel.Channels.IChannel)client).Close();
            _fac.Close();
            Console.WriteLine("[DONE]");
        }

        private void comboBox1_SelectedIndexChanged(object sender, EventArgs e)
        {
            _order.Type = ((ComboBox) sender).SelectedItem.ToString();
        }

        private void numericUpDown1_ValueChanged(object sender, EventArgs e)
        {
            _order.Price = (double) ((NumericUpDown) sender).Value;
        }

        private void button1_Click(object sender, EventArgs e)
        {
            _calc.Add(_order);
        }

        private void button2_Click(object sender, EventArgs e)
        {
            Receipt r = _calc.Checkout();
            richTextBox1.Text = r.Summary + "\n" + "Total Price = " + r.Price;            
            // reset
            _calc = StartClient(new QpidBinding("192.168.1.14", 5673));            
        }

      
    }
}
