#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint custom_activity_recognition.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'custom_activity_recognition'
  s.version          = '0.0.1'
  s.summary          = 'Custom activity recognition plugin'
  s.description      = <<-DESC
A Flutter plugin for activity recognition on iOS.
                       DESC
  s.homepage         = 'https://github.com/thorito/custom_activity_recognition'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'None' => 'victor.villar.misa@gmail.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'
  s.framework = 'CoreMotion'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # If your plugin requires a privacy manifest, for example if it uses any
  # required reason APIs, update the PrivacyInfo.xcprivacy file to describe your
  # plugin's privacy impact, and then uncomment this line. For more information,
  # see https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
  # s.resource_bundles = {'custom_activity_recognition_privacy' => ['Resources/PrivacyInfo.xcprivacy']}
end
