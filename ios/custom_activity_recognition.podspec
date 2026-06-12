Pod::Spec.new do |s|
  s.name             = 'custom_activity_recognition'
  s.version          = '0.0.1'
  s.summary          = 'Flutter plugin to recognize user activity transitions.'
  s.description      = <<-DESC
Plugin to recognize user activity transition on Android and iOS platforms.
                       DESC
  s.homepage         = 'https://github.com/thorito/custom_activity_recognition'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'thorito' => '' }
  s.source           = { :path => '.' }
  s.source_files     = 'custom_activity_recognition/Sources/custom_activity_recognition/**/*'
  s.resources        = 'Assets/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'

  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
  s.frameworks = 'CoreMotion'
end
