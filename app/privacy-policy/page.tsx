export default function PrivacyPolicyPage() {
    return (
        <main style={{ maxWidth: 800, margin: "40px auto", padding: 16 }}>
            <h1 className="text-3xl">Privacy Policy</h1>

            <p><strong>Last updated:</strong> 14 December 2025</p>

            <p className="py-5">
                This Privacy Policy describes how we collect, use, and protect your information
                when you use our application.
            </p>

            <h2 className="py-2 font-bold text-2xl">Information Collection</h2>
            <p>
                We do not collect or share personal data. All sensitive data remains on your device.
            </p>

            <h2 className="py-2 font-bold text-2xl">Security</h2>
            <p>
                Data is protected using Android system security features including encryption
                and biometric authentication.
            </p>

            <h2 className="py-2 font-bold text-2xl">Third-Party Services</h2>
            <p>
                We may use firebase analytics services to understand app usage.
                These services do not access your stored content.
            </p>

            <h2 className="py-2 font-bold text-2xl">Contact</h2>
            <p>
                If you have questions, contact us at:
                <br />
                <a href="mailto:support@example.com" className="text-blue-600">secret-vault-support@rignis.com</a>
            </p>
        </main>
    )
}
