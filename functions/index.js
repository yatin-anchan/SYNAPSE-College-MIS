const functions = require("firebase-functions");
const nodemailer = require("nodemailer");
const admin = require("firebase-admin");

admin.initializeApp();

// Configure your email service
const transporter = nodemailer.createTransport({
  service: "yahoo",
  auth: {
    user: "no_reply_synapse_mis@yahoo.com",
    pass: "SynapseMIS@2026",
  },
});

exports.sendFacultyInvitation = functions.https.onCall(
    async (data, context) => {
      const {to, facultyName, facultyId} = data;

      const mailOptions = {
        from: "SYNAPSE MIS <no_reply_synapse_mis@yahoo.com>",
        to: to,
        subject: "Welcome to SYNAPSE MIS - Complete Your Profile",
        html: `
      <div style="font-family: Arial, sans-serif;
      padding: 20px; background-color: #f4f4f4;">
        <div style="max-width: 600px; margin: 0 auto;
        background-color: white; padding: 30px;
        border-radius: 10px;">
          <h2 style="color: #6C63FF;">Welcome to SYNAPSE MIS!</h2>
          <p>Dear ${facultyName},</p>
          <p>You have been added to the SYNAPSE
          Management Information System.</p>
          <p>Please complete your profile setup by
          clicking the button below:</p>
          <a href="https://synapsemis.page.link/setup?facultyId=${facultyId}"
             style="display: inline-block;
             padding: 12px 30px;
             background-color: #6C63FF;
             color: white;
             text-decoration: none;
             border-radius: 5px;
             margin: 20px 0;">
            Complete Profile Setup
          </a>
          <p style="color: #666; font-size: 14px;">
            If the button doesn't work,
            copy and paste this link in your browser:<br>
            <code>synapsemis://setup?facultyId=${facultyId}</code>
          </p>
          <hr style="margin: 30px 0;
          border: none;
          border-top: 1px solid #ddd;">
          <p style="color: #999; font-size: 12px;">
            This is an automated email from SYNAPSE MIS.
            Please do not reply to this email.
          </p>
        </div>
      </div>
    `,
      };

      try {
        await transporter.sendMail(mailOptions);
        return {success: true, message: "Email sent successfully"};
      } catch (error) {
        console.error("Error sending email:", error);
        throw new functions.https.HttpsError(
            "internal",
            "Failed to send email",
        );
      }
    },
);
